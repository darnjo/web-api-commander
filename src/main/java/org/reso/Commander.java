package org.reso;

import jdk.internal.util.xml.impl.XMLWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.WriterOutputStream;
import org.apache.commons.vfs2.FileUtil;
import org.apache.log4j.Logger;
import org.apache.olingo.client.api.EdmEnabledODataClient;
import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.communication.request.retrieve.EdmMetadataRequest;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntitySetRequest;
import org.apache.olingo.client.api.communication.request.retrieve.XMLMetadataRequest;
import org.apache.olingo.client.api.communication.response.ODataResponse;
import org.apache.olingo.client.api.communication.response.ODataRetrieveResponse;
import org.apache.olingo.client.api.data.ResWrap;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.api.edm.xml.XMLMetadata;
import org.apache.olingo.client.api.serialization.ODataSerializer;
import org.apache.olingo.client.api.serialization.ODataSerializerException;
import org.apache.olingo.client.api.uri.URIBuilder;
import org.apache.olingo.client.core.EdmEnabledODataClientImpl;
import org.apache.olingo.client.core.ODataClientFactory;
import org.apache.olingo.client.core.ODataClientImpl;
import org.apache.olingo.client.core.edm.ClientCsdlXMLMetadata;
import org.apache.olingo.client.core.edm.xml.ClientCsdlEdmx;
import org.apache.olingo.client.core.serialization.JsonEntitySetSerializer;
import org.apache.olingo.commons.api.data.EntitySet;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.server.api.serializer.EdmAssistedSerializer;
import org.apache.olingo.server.core.ServiceMetadataImpl;
import org.apache.olingo.server.core.serializer.xml.MetadataDocumentXmlSerializer;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import sun.tools.jar.CommandLine;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.URI;
import java.nio.Buffer;
import java.nio.ByteBuffer;

public class Commander {
  private ODataClient client;
  private String serviceRoot;
  private String bearerToken;

  private static final Logger log = Logger.getLogger(CommandLine.class);

  /**
   *
   * @param serviceRoot
   */
  public Commander(String serviceRoot) {
    this.serviceRoot = serviceRoot;
    client = ODataClientFactory.getClient();
//    client = ODataClientFactory.getEdmEnabledClient(serviceRoot);
  }

  /**
   *
   * @param serviceRoot
   * @param bearerToken
   */
  public Commander(String serviceRoot, String bearerToken) {
    this(serviceRoot);
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
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      IOUtils.copy(request.rawExecute(), baos);
      log.info("Received metadata!");

      log.info("Writing metadata to file...");
      FileUtils.writeByteArrayToFile(new File(outputFileName), baos.toByteArray());
      log.info("File written!");

      return client.getReader().readMetadata(new ByteArrayInputStream(baos.toByteArray()));
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
   * Writes the given entity set to the given path name, or throws an exception if that Entity cannot be found at the given uri.
   * @param uri the URI used to search for that entity.
   */
  public void getEntitySet(String uri) {
    try {
      ODataEntitySetRequest<ClientEntitySet> entitySetRequest = client.getRetrieveRequestFactory().getEntitySetRequest(URI.create(uri));
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      IOUtils.copy(entitySetRequest.rawExecute(), baos);
      log.info("Received Results!");

      log.info("Writing results to file!");
      FileUtils.writeByteArrayToFile(new File("./entities.json"), baos.toByteArray());
      log.info("File written!");

    } catch (Exception ex) {
      log.error(ex.toString());
    }
  }

  /**
   * Reads entities from a given resource.
   * @param resourceName the name of the resource.
   * @return a ClientEntitySet containing any entities found.
   */
  public ClientEntitySet readEntities(String resourceName) {

    final URI uri = client.newURIBuilder(serviceRoot).appendEntitySetSegment(resourceName).build();

    final ODataRetrieveResponse<ClientEntitySet> entitySetResponse =
      client.getRetrieveRequestFactory().getEntitySetRequest(uri).execute();

    return entitySetResponse.getBody();
  }

  public void convertMetadata(String pathToEDMX) {
    try {
      TransformerFactory factory = TransformerFactory.newInstance();
      Source xslt = new StreamSource(new File("./V4-CSDL-to-OpenAPI.xslt"));
      Transformer transformer = factory.newTransformer(xslt);

      Source text = new StreamSource(new File(pathToEDMX));
      transformer.transform(text, new StreamResult(new File(pathToEDMX + ".transformed.json")));

    } catch (Exception ex) {
      log.error(ex.toString());
    }
  }

  /**
   * Writes an Entity Set to the given filename.
   * @param entitySet
   */
  private void serializeEntitySet(ClientEntitySet entitySet) {

    ODataSerializer serializer = new JsonEntitySetSerializer(false, ContentType.JSON);

    try {
      serializer.write(new FileWriter("./PropResi.json"), entitySet);
    } catch (IOException ioex) {
      System.out.println(ioex.getMessage());
    } catch (ODataSerializerException osx) {
      System.out.println(osx.getMessage());
    }
  }

}