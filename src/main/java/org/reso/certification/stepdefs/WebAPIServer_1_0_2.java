package org.reso.certification.stepdefs;

import com.github.fge.jackson.JacksonUtils;
import io.cucumber.java8.En;

import io.restassured.http.ContentType;
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
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.path.json.JsonPath.from;
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
      LOG.info("Using RESOScript: " + pathToRESOScript);
    });
    And("^Client Settings and Parameters were read from the file$", () -> {
      if (settings == null) {
        settings = Settings.loadFromRESOScript(new File(System.getProperty("pathToRESOScript")));
      }
      LOG.info("RESOScript loaded successfully!");
    });

    Given("^an OData client was successfully created from the given RESOScript$", () -> {
      serviceRoot = settings.getClientSettings().get(ClientSettings.WEB_API_URI);

      //TODO: add base64 un-encode when applicable
      bearerToken = settings.getClientSettings().get(ClientSettings.BEARER_TOKEN);
      if (bearerToken != null && bearerToken.length() > 0) {
        LOG.info("Bearer token loaded... first 4 characters: " + bearerToken.substring(0, 4));
      }

      clientId = settings.getClientSettings().get(ClientSettings.CLIENT_IDENTIFICATION);
      clientSecret = settings.getClientSettings().get(ClientSettings.CLIENT_SECRET);
      authorizationUri = settings.getClientSettings().get(ClientSettings.AUTHORIZATION_URI);
      tokenUri = settings.getClientSettings().get(ClientSettings.TOKEN_URI);
      redirectUri = settings.getClientSettings().get(ClientSettings.REDIRECT_URI);
      scope = settings.getClientSettings().get(ClientSettings.CLIENT_SCOPE);

      LOG.info("Service root is: " + serviceRoot);

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
    AtomicReference<String> jsonResponseData = new AtomicReference<>();
    AtomicReference<Integer> numResults = new AtomicReference<>();

    //REQ-WA103-END3
    When("^a metadata request is made relative to the given service root in ClientSettings_WebAPIURI$", () -> {
      request.set(Settings.resolveParameters(settings.getRequests().get(REQUESTS.WEB_API_1_0_2.REQ_WA_103_END_3), settings));

      LOG.info("Request URL: " + request.get().getUrl());

      rawRequest.set(commander.get().getClient().getRetrieveRequestFactory().getRawRequest(Commander.prepareURI(request.get().getUrl())));
      oDataRawResponse.set(rawRequest.get().execute());
    });
    Then("^the server responds with a status code of (\\d+)$", (Integer code) -> {
      int responseCode = oDataRawResponse.get().getStatusCode();
      LOG.info("Response code is: " + responseCode);
      assertEquals(code.intValue(), responseCode);
    });
    And("^the metadata returned is valid$", () -> {
      boolean isValid = commander.get().validateMetadata((oDataRawResponse.get().getRawResponse()));
      LOG.info("Metadata is: " + (isValid ? "valid" : "invalid"));
      assertTrue(isValid);
    });


    //REQ-WA103-QR3
    Given("^a case-sensitive OData \\$select list consisting of Parameter_SelectList in the given request$", () -> {
      request.set(Settings.resolveParameters(settings.getRequests().get(REQUESTS.WEB_API_1_0_2.REQ_WA_103_QR_3), settings));
      LOG.info("Request URL: " + request.get().getUrl());
      assertTrue(request.get().getUrl().contains("$select=" + settings.getParameters().getValue("SelectList")));
    });
    When("^the request is issued against the Web API Server's Parameter_EndpointResource$", () -> {
      URI requestUri = Commander.prepareURI(request.get().getUrl());

      LOG.info("Request is: " + requestUri.toString());

      rawRequest.set(commander.get().getClient().getRetrieveRequestFactory().getRawRequest(requestUri));
      oDataRawResponse.set(rawRequest.get().execute());
    });
    And("^the results contain at most Parameter_TopCount records$", () -> {
      jsonResponseData.set(convertInputStreamToString(oDataRawResponse.get().getRawResponse()));
      LOG.info("Server response is: " + jsonResponseData.get());

      //TODO: create constants for parameter or take as args to this function
      List<String> items = from(jsonResponseData.get()).getList("value");
      numResults.set(items.size());

      LOG.info("Number of values returned: " + numResults.get());
      assertTrue(numResults.get() <= Integer.parseInt(settings.getParameters().getValue("TopCount")));
    });
    And("^data are present in fields contained within Parameter_SelectList$", () -> {
      List<String> fieldList = new ArrayList<>();
      Arrays.stream(settings.getParameters().getValue("SelectList").split(",")).forEach(field -> fieldList.add(field));

      AtomicInteger numFieldsWithData = new AtomicInteger();

      from(jsonResponseData.get()).getList("value", HashMap.class).forEach(item -> {
        if (item != null) {
          fieldList.forEach(field -> {
            if (item.get(field) != null) {
              numFieldsWithData.getAndIncrement();
            }
          });
        }
      });

      LOG.info("numResults: " + numResults.get());
      LOG.info("numFields: " + fieldList.size());
      LOG.info("numFieldsWithData: " + numFieldsWithData.get());
      LOG.info("percent field fill: " + ((numResults.get() * fieldList.size()) / (1.0 * numFieldsWithData.get()) * 100) + "%");
      assertTrue(numFieldsWithData.get() > 0);
    });


    //REQ-WA103-END2
    Given("^the url for the server's DataSystem endpoint$", () -> {
      request.set(Settings.resolveParameters(settings.getRequests().get(REQUESTS.WEB_API_1_0_2.REQ_WA103_END2), settings));
      String dataSystemUrlString = request.get().getUrl();
      LOG.info("Data System URL: " + dataSystemUrlString);
      assertTrue(dataSystemUrlString.length() > 0);
    });
    When("^the request is issued against the DataSystem endpoint$", () -> {
      rawRequest.set(commander.get().getClient().getRetrieveRequestFactory().getRawRequest(Commander.prepareURI(request.get().getUrl())));
      oDataRawResponse.set(rawRequest.get().execute());
      jsonResponseData.set(convertInputStreamToString(oDataRawResponse.get().getRawResponse()));
      LOG.info("Response is: " + jsonResponseData.get());
    });
    And("^the results are valid JSON$", () -> {
      assertTrue(JacksonUtils.getReader().readTree(jsonResponseData.get()).size() > 0);
    });
    And("^the results match the expected DataSystem schema$", () -> {
    });
  }

  private static class REQUESTS {
    private static class WEB_API_1_0_2 {
      private static final String REQ_WA_103_END_3 = "REQ-WA103-END3";
      private static final String REQ_WA_103_QR_3 = "REQ-WA103-QR3";
      private static final String REQ_WA103_END2 = "REQ-WA103-END2";
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
