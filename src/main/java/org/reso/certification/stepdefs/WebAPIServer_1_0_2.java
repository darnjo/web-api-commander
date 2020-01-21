package org.reso.certification.stepdefs;

import io.cucumber.java8.En;

import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.olingo.client.api.communication.request.retrieve.ODataRawRequest;
import org.apache.olingo.client.api.communication.response.ODataRawResponse;
import org.reso.commander.Commander;
import org.reso.models.ClientSettings;
import org.reso.models.Request;
import org.reso.models.Settings;

import java.io.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class WebAPIServer_1_0_2 implements En {
  private static final Logger LOG = LogManager.getLogger(WebAPIServer_1_0_2.class);

  private Response response;
  private ValidatableResponse json;
  private RequestSpecification request;

  private static Settings settings;
  private String serviceRoot, bearerToken, clientId, clientSecret, authorizationUri, tokenUri, redirectUri, scope;
  private String pathToRESOScript;

  public WebAPIServer_1_0_2() {

    AtomicReference<Commander> commander = new AtomicReference<>();
    //Background
    Given("^a RESOScript file was provided$", () -> {
      if (pathToRESOScript == null) {
        pathToRESOScript = System.getProperty("pathToRESOScript");
      }
      LOG.debug("Using RESOScript: " + pathToRESOScript);
    });
    And("^Client Settings and Parameters were read from the file$", () -> {
      if (settings == null) {
        settings = Settings.loadFromRESOScript(new File(System.getProperty("pathToRESOScript")));
      }
      LOG.debug("RESOScript loaded successfully!");
    });

    Given("^an OData client was successfully created from the given RESOScript$", () -> {
      serviceRoot = settings.getClientSettings().get(ClientSettings.WEB_API_URI);

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
          .useEdmEnabledClient(true)
          .build());
    });

    //the following items are reused by subsequent tests
    AtomicReference<ODataRawResponse> oDataRawResponse = new AtomicReference<>();
    AtomicReference<Request> request = new AtomicReference<>();
    AtomicReference<ODataRawRequest> rawRequest = new AtomicReference<>();

    //REQ-WA103-END3
    When("^a /\\$metadata request is issued relative to the ClientSettings_WebAPIURI$", () -> {
      request.set(Settings.resolveParameters(settings.getRequests().get(REQUESTS.WEB_API_1_0_2.REQ_WA_103_END_3), settings));

      LOG.debug("Request URL: " + request.get().getUrl());

      rawRequest.set(commander.get().getClient().getRetrieveRequestFactory().getRawRequest(Commander.prepareURI(request.get().getUrl())));
      oDataRawResponse.set(rawRequest.get().execute());
    });

    Then("^the server responds with a status code of (\\d+)$", (Integer code) -> {
      assertEquals(code.intValue(), oDataRawResponse.get().getStatusCode());
    });
    And("^the metadata returned is valid$", () -> {
      assertTrue(commander.get().validateMetadata((oDataRawResponse.get().getRawResponse())));
    });

    //REQ-WA103-QR3
    Given("^a case-sensitive OData \\$select list consisting of Parameter_SelectList in the given request$", () -> {
      request.set(Settings.resolveParameters(settings.getRequests().get(REQUESTS.WEB_API_1_0_2.REQ_WA_103_QR_3), settings));
      LOG.debug("Request URL: " + request.get().getUrl());
    });
    When("^the request is issued against the Web API Server's Parameter_EndpointResource$", () -> {
      rawRequest.set(commander.get().getClient().getRetrieveRequestFactory().getRawRequest(Commander.prepareURI(request.get().getUrl())));
      oDataRawResponse.set(rawRequest.get().execute());
    });
    /* NOTE: response code testing is done with the Then above ^^ */
    And("^the results contain at most Parameter_TopCount records with data present in the fields in Parameter_SelectList$", () -> {
      String jsonString = convertInputStreamToString(oDataRawResponse.get().getRawResponse());
      LOG.debug("Server response is: " + jsonString);
    });
  }

  private static class REQUESTS {
    private static class WEB_API_1_0_2 {
      private static final String REQ_WA_103_END_3 = "REQ-WA103-END3";
      private static final String REQ_WA_103_QR_3 = "REQ-WA103-QR3";
    }
  }

  private static String convertInputStreamToString(InputStream inputStream) {
    InputStreamReader isReader = new InputStreamReader(inputStream);
    BufferedReader reader = new BufferedReader(isReader);
    StringBuffer sb = new StringBuffer();
    String str;
    try {
      while((str = reader.readLine())!= null){
        sb.append(str);
      }
      return sb.toString();
    } catch (Exception ex) {
      LOG.error("Error in convertInputStreamToString: " + ex);
    }
    return null;
  }
}
