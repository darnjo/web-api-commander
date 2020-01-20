package org.reso.certification.stepdefs;

import io.cucumber.java8.En;

import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.olingo.client.api.communication.request.retrieve.ODataRawRequest;
import org.apache.olingo.client.api.communication.response.ODataRawResponse;
import org.apache.olingo.client.api.edm.xml.XMLMetadata;
import org.apache.olingo.commons.api.format.ContentType;
import org.reso.commander.Commander;
import org.reso.models.ClientSettings;
import org.reso.models.Request;
import org.reso.models.Settings;

import java.io.File;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

import static io.restassured.RestAssured.given;

public class WebAPIServer_1_0_2 implements En {
  private static final Logger LOG = LogManager.getLogger(WebAPIServer_1_0_2.class);

  private Response response;
  private ValidatableResponse json;
  private RequestSpecification request;

  //created with the commanderBuilder throughout the initialization body
  private static Commander commander;
  private static Settings settings;

  private String serviceRoot, bearerToken, clientId, clientSecret, authorizationUri, tokenUri, redirectUri, scope;
  private String resoScriptFileName;

  public WebAPIServer_1_0_2() {

    AtomicReference<Commander> commander = new AtomicReference<>();
    //Background
    Given("^a RESOScript file was provided$", () -> {
      resoScriptFileName = System.getProperty("pathToRESOScript");
      LOG.debug("Loading RESOScript: " + resoScriptFileName);
    });
    And("^Client Settings and Parameters were read from the file$", () -> {
      settings = Settings.loadFromRESOScript(new File(resoScriptFileName));
      LOG.debug("RESOScript loaded successfully!");
    });

    Given("^an OData client was successfully created from the given RESOScript$", () -> {
      serviceRoot = settings.getClientSettings().get(ClientSettings.WEB_API_URI);
      LOG.debug("Service Root is:" + serviceRoot);

      //TODO: add base64 un-encode when applicable
      bearerToken = settings.getClientSettings().get(ClientSettings.BEARER_TOKEN);
      if (bearerToken != null && bearerToken.length() > 0) {
        LOG.debug("Bearer token loaded... first 4 characters: " + bearerToken.substring(0, 4));
      }

      clientId = settings.getClientSettings().get(ClientSettings.CLIENT_IDENTIFICATION);
      clientSecret = settings.getClientSettings().get(ClientSettings.CLIENT_SECRET);
      authorizationUri = settings.getClientSettings().get(ClientSettings.AUTHORIZATION_URI);
      tokenUri = settings.getClientSettings().get(ClientSettings.TOKEN_URI);
      redirectUri = settings.getClientSettings().get(ClientSettings.REDIRECT_URI);
      scope = settings.getClientSettings().get(ClientSettings.CLIENT_SCOPE);

      LOG.debug("Service root is: " + serviceRoot);

      //create Commander instance
      commander.set(new Commander.Builder()
          .clientId(clientId)
          .clientSecret(clientSecret)
          .authorizationUri(authorizationUri)
          .tokenUri(tokenUri)
          .redirectUri(redirectUri)
          .scope(scope)
          .serviceRoot(serviceRoot)
          .bearerToken(bearerToken)
          .build());
    });

    AtomicReference<ODataRawResponse> oDataRawResponse = new AtomicReference<>();
    When("^a metadata request is made relative to the given Web API service root$", () -> {
      Request request = Settings.resolveParameters(settings.getRequests().get(REQUESTS.WEB_API_1_0_2.METADATA), settings);
      ODataRawRequest rawRequest = commander.get().getClient().getRetrieveRequestFactory().getRawRequest(URI.create(request.getUrl()));
      oDataRawResponse.set(rawRequest.execute());
    });
    Then("^the server responds with a status code of \"([^\"]*)\"$", (String code) -> {
      assertEquals(Integer.parseInt(code), oDataRawResponse.get().getStatusCode());
    });
    And("^the metadata returned is valid$", () -> {
      // deserialize metadata from given file
      XMLMetadata metadata = commander
                              .get()
                              .getClient()
                              .getDeserializer(ContentType.APPLICATION_XML)
                              .toMetadata(oDataRawResponse.get().getRawResponse());

      assertTrue(commander.get().validateMetadata(metadata));
    });
  }

  private static class REQUESTS {
    private static class WEB_API_1_0_2 {
      private static final String METADATA = "REQ-WA103-END3";
    }
  }
}
