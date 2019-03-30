package org.reso;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.ODataClientBuilder;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntitySetRequest;
import org.apache.olingo.client.api.communication.request.retrieve.XMLMetadataRequest;
import org.apache.olingo.client.api.communication.response.ODataEntityCreateResponse;
import org.apache.olingo.client.api.communication.response.ODataRawResponse;
import org.apache.olingo.client.api.communication.response.ODataRetrieveResponse;
import org.apache.olingo.client.api.data.ResWrap;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.api.edm.xml.XMLMetadata;
import org.apache.olingo.client.api.serialization.ClientODataDeserializer;
import org.apache.olingo.client.api.serialization.ODataDeserializerException;
import org.apache.olingo.client.api.serialization.ODataSerializer;
import org.apache.olingo.client.core.ODataClientFactory;
import org.apache.olingo.client.core.domain.ClientEntitySetImpl;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeException;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.serializer.EntityCollectionSerializerOptions;
import org.apache.olingo.server.core.uri.queryoption.CountOptionImpl;

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
  private boolean useEdmEnabledClient;

  private static final Logger log = Logger.getLogger(Commander.class);

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
    this.useEdmEnabledClient = useEdmEnabledClient;

    this.serviceRoot = serviceRoot;
    log.debug("\nUsing EdmEnabledClient: " + useEdmEnabledClient);
    if (useEdmEnabledClient) {
      client = ODataClientFactory.getEdmEnabledClient(serviceRoot);
    } else {
      client = ODataClientFactory.getClient();
    }
  }

  /**
   * Creates a Commander instance that uses the normal OData client and the given Bearer Token.
   * @param serviceRoot the serviceRoot of the WebAPI server.
   * @param bearerToken the bearer token used to authenticate with the given serviceRoot.
   */
  public Commander(String serviceRoot, String bearerToken) {
    this(serviceRoot, bearerToken, false);
    this.bearerToken = bearerToken;
  }

  /**
   * Creates a Commander instance that uses the given Bearer token for authentication and allows the Client
   * to specify whether to use an EdmEnabledClient or normal OData client.
   *
   * NOTE: serviceRoot can sometimes be null, but is required if useEdmEnabledClient is true.
   *        A check has been added for this condition.
   *
   * TODO: replace constructors with Builder pattern.
   *
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
   *
   * TODO: add optional validation upon fetch
   *
   * @return Edm representation of the server metadata.
   */
  public Edm getMetadata(String outputFileName) {
    XMLMetadataRequest request = client.getRetrieveRequestFactory().getXMLMetadataRequest(serviceRoot);

    try {
      log.info("Fetching Metadata from " + serviceRoot + "...");
      byte[] buffer = IOUtils.toByteArray(request.rawExecute());
      log.info("Transfer complete! Bytes received: " + buffer.length);

      // copy response to given output file
      FileUtils.writeByteArrayToFile(new File(outputFileName), buffer);
      log.info("Wrote metadata to: " + outputFileName);

      // read metadata from binary to ensure it deserializes properly and return
      return client.getReader().readMetadata(new ByteArrayInputStream(buffer));
    } catch (Exception ex) {
      System.err.println(ex.toString());
      System.exit(1);
    }

    return null;
  }

  /**
   * Validates given XMLMetadata
   * @param metadata the XMLMetadata to be validated
   * @return true if the metadata is valid, meaning that it's also a valid OData 4 Service Document
   */
  public boolean validateMetadata(XMLMetadata metadata) {
    try {
      // call the probably-useless metadata validator. can't hurt though
      // SEE: https://github.com/apache/olingo-odata4/blob/master/lib/client-core/src/main/java/org/apache/olingo/client/core/serialization/ODataMetadataValidationImpl.java#L77-L116
      client.metadataValidation().validateMetadata(metadata);

      // also check whether metadata contains a valid service document in OData v4 format
      return client.metadataValidation().isServiceDocument(metadata)
          && client.metadataValidation().isV4Metadata(metadata);
    } catch (Exception ex) {
      log.error("ERROR: " + ex.getMessage());

      if (ex.getCause() != null) {
        log.error("ERROR: " + ex.getCause().getMessage());
        System.exit(1);
      }
    }

    return false;
  }

  /**
   * Validates the given metadata contained in the given file path.
   * @param pathToEdmx the path to look for metadata in. Assumes metadata is stored as XML.
   * @return true if the metadata is valid and false otherwise.
   */
  public boolean validateMetadata(String pathToEdmx) {
    try {
      // deserialize metadata from given file
      XMLMetadata metadata =
          client.getDeserializer(ContentType.APPLICATION_XML).toMetadata(new FileInputStream(pathToEdmx));

      return validateMetadata(metadata);

    } catch (Exception ex) {
      log.error("ERROR: " + ex.getMessage());
      System.exit(1);
    }
    return false;
  }

  /**
   * Retrieves a client entity set using the given requestURI, but doesn't perform automatic paging
   * in the way that readEntitySet does. If the Commander has been instantiated with an EdmEnabledClient,
   * results will be validated against server metadata while being fetched.
   *
   * @param requestURI the full OData WebAPI URI used to fetch records. requestURI is expected to be URL Encoded.
   * @return a ClientEntitySet containing the requested records, or null if nothing was found.
   */
  public ClientEntitySet getEntitySet(URI requestURI) {
    try {
      ODataRetrieveResponse<ClientEntitySet> response
          = client.getRetrieveRequestFactory().getEntitySetRequest(requestURI).execute();

      if (response.getStatusCode() == HttpStatusCode.OK.getStatusCode()) {
        return response.getBody();
      } else {
        log.error("ERROR:getEntitySet received a response status other than OK (200)!");
        System.exit(1);
      }
    } catch (IllegalArgumentException iaex) {
      if (useEdmEnabledClient) {
        log.error("\nError encountered while using the EdmEnabledClient. This usually means metadata were invalid!");
      }
      log.error(iaex.getMessage());
      System.exit(1);
    } catch (Exception ex) {
      log.error(ex.toString());
      System.exit(1);
    }

    return null;
  }

  /**
   * Executes a get request on URI and saves raw response to outputFilePath.
   * @param requestURI the URI to make the request against
   * @param outputFilePath the outputFilePath to write the response to
   */
  public void saveRawGetRequest(URI requestURI, String outputFilePath) {
    try {
      FileUtils.copyInputStreamToFile(
          client.getRetrieveRequestFactory().getRawRequest(requestURI).rawExecute(), new File(outputFilePath));

      log.info("Request complete... Response written to file: " + outputFilePath);

    } catch (Exception ex) {
      log.error("ERROR: exception occurred in writeRawResponse. " + ex.getMessage());
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
    ODataRetrieveResponse<ClientEntitySet> entitySetResponse = null;

    do {
      entitySetResponse = client.getRetrieveRequestFactory().getEntitySetRequest(uri).execute();

      result.addAll(entitySetResponse.getBody().getEntities());

      uri = client.newURIBuilder(serviceRoot).appendEntitySetSegment(resourceName).skip(result.size()).build();

    } while (entitySetResponse.getBody().getNext() != null && (limit == -1 || result.size() < limit));

    //limit result to limit as we may have paged further than needed
    ClientEntitySet val = new ClientEntitySetImpl();
    val.getEntities().addAll(result.subList(0, limit == -1 ? result.size() : Math.min(limit, result.size())));
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
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
  }

}