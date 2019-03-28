package org.reso;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntitySetRequest;
import org.apache.olingo.client.api.communication.request.retrieve.XMLMetadataRequest;
import org.apache.olingo.client.api.communication.response.ODataRetrieveResponse;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.api.edm.xml.XMLMetadata;
import org.apache.olingo.client.core.ODataClientFactory;
import org.apache.olingo.client.core.domain.ClientEntitySetImpl;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.format.ContentType;
import sun.tools.jar.CommandLine;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;


/**
 * Most of the work done by the WebAPI commander is done by this class. Its public methods are, therefore,
 * the ones the Client programmer is expected to use.
 */
public class Commander {
  private ODataClient client;
  private String serviceRoot;
  private String bearerToken;

  private static final Logger log = Logger.getLogger(CommandLine.class);

  /**
   * Creates a Commander instance that uses the normal OData client.
   * In other words, no validation is done against metadata upon making requests.
   * @param serviceRoot
   */
  public Commander(String serviceRoot) {
    this(serviceRoot, false);
  }

  /**
   * Creates a Commander instance that allows the caller to use an EdmEnabledClient,
   * meaning that all payloads will be verified against the metadata published at serviceRoot.
   * @param serviceRoot the service root of the WebAPI server.
   * @param useEdmEnabledClient true if an EdmEnabledClient should be used, and false otherwise.
   */
  public Commander(String serviceRoot, boolean useEdmEnabledClient) {
    this.serviceRoot = serviceRoot;
    log.info("Using EdmEnabledClient: " + useEdmEnabledClient);
    client = useEdmEnabledClient ? ODataClientFactory.getEdmEnabledClient(serviceRoot) : ODataClientFactory.getClient();
  }

  /**
   * Creates a Commander instance that uses the normal OData client and the given Bearer Token.
   * @param serviceRoot the serviceRoot of the WebAPI server.
   * @param bearerToken the bearer token used to authenticate with the given serviceRoot.
   */
  public Commander(String serviceRoot, String bearerToken) {
    this(serviceRoot, bearerToken, false);
  }

  /**
   * Creates a Commander instance that uses the given Bearer token for authentication and allows the Client
   * to specify whether to use an EdmEnabledClient or normal OData client.
   * TODO: replace constructors with Builder pattern.
   * @param serviceRoot the service root of the WebAPI server.
   * @param bearerToken the bearer token to use to authenticate with the given serviceRoot.
   * @param useEdmEnabledClient
   */
  public Commander(String serviceRoot, String bearerToken, boolean useEdmEnabledClient) {
    this(serviceRoot, useEdmEnabledClient);
    this.bearerToken = bearerToken;
    client.getConfiguration().setHttpClientFactory(new TokenHttpClientFactory(bearerToken));
  }

  /**
   * Gets server metadata in EDMX format.
   * @return Edm representation of the server metadata.
   */
  public Edm getMetadata(String outputFileName) {
    XMLMetadataRequest request = client.getRetrieveRequestFactory().getXMLMetadataRequest(serviceRoot);

    try {
      log.info("Fetching Metadata...");
      ByteArrayOutputStream response = new ByteArrayOutputStream();
      IOUtils.copy(request.rawExecute(), response);
      log.info("Received metadata!");

      log.info("Writing metadata to file...");
      FileUtils.writeByteArrayToFile(new File(outputFileName), response.toByteArray());
      log.info("File written!");

      return client.getReader().readMetadata(new ByteArrayInputStream(response.toByteArray()));
    } catch (Exception ex) {
      System.err.println(ex.toString());
    }
    return null;
  }

  /**
   * Validates the given metadata at the given file path name.
   * @param metadataFileName the path name to look for the metadata in.
   * @return true if the metadata is valid and false otherwise.
   */
  public boolean validateMetadata(String metadataFileName) {
    try {
      XMLMetadata metadata = client.getDeserializer(ContentType.APPLICATION_XML).toMetadata(new FileInputStream(metadataFileName));
      return client.metadataValidation().isServiceDocument(metadata) && client.metadataValidation().isV4Metadata(metadata);
    } catch (Exception ex) {
      log.error(ex.getMessage());
      //TODO
      //log.error(ex.getCause().getMessage());
    }
    return false;
  }

  /**
   * Writes the given entity set to the given path name, or throws an exception if that Entity
   * cannot be found at the given uri.
   * @param uri the URI used to search for that entity.
   */
  public void getEntitySet(String uri) {
    try {
      URI requestURI = URI.create(uri);
      ODataEntitySetRequest<ClientEntitySet> entitySetRequest
          = client.getRetrieveRequestFactory().getEntitySetRequest(requestURI);

      ByteArrayOutputStream response = new ByteArrayOutputStream();
      IOUtils.copy(entitySetRequest.rawExecute(), response);
      log.info("Received Results!");

      log.info("Writing results to file!");
      FileUtils.writeByteArrayToFile(new File(requestURI.getPath() + ".json"), response.toByteArray());
      log.info("File written!");

    } catch (Exception ex) {
      log.error(ex.toString());
    }
  }

  /**
   * Reads entities from a given resource.
   * TODO: currently reads items into memory and writes a file. Instead, should incrementally write to file and track of progress.
   *
   * @param resourceName the name of the resource.
   * @param limit the limit for the number of records to read from the Server. Use -1 to fetch all.
   * @return a ClientEntitySet containing any entities found.
   */
  public ClientEntitySet readEntities(String resourceName, int limit) {

    List<ClientEntity> result = new ArrayList<>();
    URI uri = client.newURIBuilder(serviceRoot).appendEntitySetSegment(resourceName).build();

    do {
      ODataRetrieveResponse<ClientEntitySet> entitySetResponse =
          client.getRetrieveRequestFactory().getEntitySetRequest(uri).execute();

      result.addAll(entitySetResponse.getBody().getEntities());

      //note that uri becomes null when the last page has been reached
      uri = entitySetResponse.getBody().getNext();

    } while (uri != null && (limit == -1 || result.size() < limit));

    //limit result to limit as we may have paged further than needed
    ClientEntitySet val = new ClientEntitySetImpl();
    val.getEntities().addAll(result.subList(0, limit > 0 ? limit : result.size()));
    return val;
  }

  /**
   * Converts metadata in EDMX format to metadata in Swagger 2.0 format.
   * Converted file will have the same name as the input file, with .swagger.json appended to the name.
   * @param pathToEDMX the metadata file to convert.
   */
  public void convertMetadata(String pathToEDMX) {
    try {
      TransformerFactory factory = TransformerFactory.newInstance();
      Source xslt = new StreamSource(new File("./V4-CSDL-to-OpenAPI.xslt"));
      Transformer transformer = factory.newTransformer(xslt);

      Source text = new StreamSource(new File(pathToEDMX));
      transformer.transform(text, new StreamResult(new File(pathToEDMX + ".swagger.json")));

    } catch (Exception ex) {
      log.error(ex.toString());
    }
  }

  /**
   * Writes an Entity Set to the given outputFilePath.
   * @param entitySet - the entity set to save
   * @param outputFilePath - the path to write the file to.
   */
  public void serializeEntitySet(ClientEntitySet entitySet, String outputFilePath) {

    try {
      log.info("Serializing " + entitySet.getEntities().size() + " item(s) to " + outputFilePath);
      client.getSerializer(ContentType.JSON).write(new FileWriter(outputFilePath), client.getBinder().getEntitySet(entitySet));

    //FileUtils.copyInputStreamToFile(client.getWriter().writeEntities(entitySet.getEntities(), ContentType.JSON_FULL_METADATA), new File(outputFilePath));
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
  }

}