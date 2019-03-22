package org.reso;

import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.communication.request.retrieve.EdmMetadataRequest;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntitySetRequest;
import org.apache.olingo.client.api.communication.response.ODataRetrieveResponse;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.api.serialization.ODataSerializer;
import org.apache.olingo.client.api.serialization.ODataSerializerException;
import org.apache.olingo.client.api.uri.URIBuilder;
import org.apache.olingo.client.core.ODataClientFactory;
import org.apache.olingo.client.core.serialization.JsonEntitySetSerializer;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.format.ContentType;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;

public class Commander {
  private ODataClient client;
  private String serviceRoot;
  private String bearerToken;

  //use this constructor for bearer tokens
  public Commander(String serviceRoot) {
    this.serviceRoot = serviceRoot;
    client = ODataClientFactory.getClient();
  }

  public Commander(String serviceRoot, String bearerToken) {
    this(serviceRoot);
    this.bearerToken = bearerToken;
    client.getConfiguration().setHttpClientFactory(new TokenHttpClientFactory(bearerToken));
  }

  public Edm getMetadata() {
    EdmMetadataRequest request = client.getRetrieveRequestFactory().getMetadataRequest(serviceRoot);
    ODataRetrieveResponse<Edm> response = request.execute();
    return response.getBody();
  }

  public ClientEntitySet readEntities(String resourceName) {

    final URI uri = client.newURIBuilder(serviceRoot).appendEntitySetSegment(resourceName).build();

    final ODataRetrieveResponse<ClientEntitySet> entitySetResponse =
      client.getRetrieveRequestFactory().getEntitySetRequest(uri).execute();

    return entitySetResponse.getBody();
  }

  public void serializeEntitySet(ClientEntitySet entitySet) {

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