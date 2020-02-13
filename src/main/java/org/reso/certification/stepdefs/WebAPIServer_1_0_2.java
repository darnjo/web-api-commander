package org.reso.certification.stepdefs;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.restassured.path.json.JsonPath.from;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WebAPIServer_1_0_2 implements En {
  private static final Logger LOG = LogManager.getLogger(WebAPIServer_1_0_2.class);

  private Response response;
  private ValidatableResponse json;
  private RequestSpecification request;

  private static Settings settings;
  private String serviceRoot, bearerToken, clientId, clientSecret, authorizationUri, tokenUri, redirectUri, scope;
  private String pathToRESOScript;

  public WebAPIServer_1_0_2() {

    //the following items are reused by subsequent tests
    //TODO: make the tests more functional in the sense that they don't use local variables
    AtomicReference<Commander> commander = new AtomicReference<>();
    AtomicReference<ODataRawResponse> oDataRawResponse = new AtomicReference<>();
    AtomicReference<Request> request = new AtomicReference<>();
    AtomicReference<ODataRawRequest> rawRequest = new AtomicReference<>();
    AtomicReference<String> responseData = new AtomicReference<>();
    AtomicReference<Integer> numResults = new AtomicReference<>();

    //TODO: split test glue code into separate files after the dust settles. Sections separated by comments for now...

    /*
     * Background
     */
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


    /*
     * REQ-WA103-END3
     */
    And("^the metadata returned is valid$", () -> {
      //in this case we need to interpret the result as XML Metadata from the given response string
      XMLMetadata metadata
          = commander.get().getClient().getDeserializer(ContentType.APPLICATION_XML)
            .toMetadata(new ByteArrayInputStream(responseData.get().getBytes()));

      boolean isValid = commander.get().validateMetadata(metadata);
      LOG.info("Metadata is: " + (isValid ? "valid" : "invalid"));
      assertTrue(isValid);
    });


    /*
     * REQ-WA103-QR3
     */
    And("^the results contain at most \"([^\"]*)\" records$", (String topCountSetting) -> {
      List<String> items = from(responseData.get()).getList("value");
      numResults.set(items.size());

      int topCount = Integer.parseInt(Utils.resolveValue(topCountSetting, settings));
      LOG.info("Number of values returned: " + numResults.get() + ", top count is: " + topCount);

      assertTrue(numResults.get() > 0 && numResults.get() <= topCount);
    });
    And("^data are present in fields contained within \"([^\"]*)\"$", (String selectListSetting) -> {
      AtomicInteger numFieldsWithData = new AtomicInteger();
      List<String> fieldList = new ArrayList<>(Arrays.asList(Utils.resolveValue(selectListSetting, settings).split(",")));

      //iterate over the items and count the number of fields with data to determine whether there are data present
      from(responseData.get()).getList("value", HashMap.class).forEach(item -> {
        if (item != null) {
          fieldList.forEach(field -> {
            if (item.get(field) != null) {
              numFieldsWithData.getAndIncrement();
            }
          });
        }
      });

      LOG.info("Number of Results: " + numResults.get());
      LOG.info("Number of Fields: " + fieldList.size());
      LOG.info("Field with Data: " + numFieldsWithData.get());
      LOG.info("Percent Fill: " + ((numResults.get() * fieldList.size()) / (1.0 * numFieldsWithData.get()) * 100) + "%");
      assertTrue(numFieldsWithData.get() > 0);
    });

    /*
     * REQ-WA103-END2
     */
    And("^the results match the expected DataSystem JSON schema$", () -> {
      //TODO - need to add JSON Schema for DataSystem
    });

    /*
     * REQ-WA103-QR1
     */
    And("^the provided \"([^\"]*)\" is returned in the \"([^\"]*)\" field$", (String uniqueIdValueSetting, String uniqueIdSetting) -> {
      String expectedValue = Utils.resolveValue(uniqueIdValueSetting, settings),
          resolvedValue = from(responseData.get()).get(Utils.resolveValue(uniqueIdSetting, settings));

      LOG.info("Expected Value is:" + expectedValue);
      LOG.info("Resolved value is:" + resolvedValue);

      assertEquals(expectedValue, resolvedValue);
    });

    //==================================================================================================================
    // Common Methods
    //==================================================================================================================

    /*
     * GET request by requirementId (see generic.resoscript)
     */
    When("^a GET request is made to the resolved Url in \"([^\"]*)\"$", (String requirementId) -> {
      request.set(Settings.resolveParameters(settings.getRequests().get(requirementId), settings));

      LOG.info("Request URL: " + request.get().getUrl());

      rawRequest.set(commander.get().getClient().getRetrieveRequestFactory().getRawRequest(Commander.prepareURI(request.get().getUrl())));
      oDataRawResponse.set(rawRequest.get().execute());
      responseData.set(convertInputStreamToString(oDataRawResponse.get().getRawResponse()));

      LOG.info("Request succeeded..." + responseData.get().getBytes().length
          + " bytes received.");
    });

    /*
     * Assert response code
     */
    Then("^the server responds with a status code of (\\d+)$", (Integer code) -> {
      int responseCode = oDataRawResponse.get().getStatusCode();
      LOG.info("Response code is: " + responseCode);
      assertEquals(code.intValue(), responseCode);
    });

    /*
     * Assert greater than: lValFromItem > rValFromSetting
     *
     * TODO: add general op expression parameter rather than creating individual comparators
     */
    And("^data in \"([^\"]*)\" are greater than \"([^\"]*)\"$", (String lValFromItem, String rValFromSetting) -> {
      from(responseData.get()).getList("value", HashMap.class).forEach(item -> {
        Integer lVal = new Integer(item.get(Utils.resolveValue(lValFromItem, settings)).toString()),
                rVal = new Integer(Utils.resolveValue(rValFromSetting, settings));

        assertTrue( lVal > rVal );
      });
    });

    /*
     * validate XML wrapper
     */
    And("^the response is valid XML$", () -> {
      assertTrue(Commander.validateXML(responseData.get()));

      LOG.info("Response is valid XML!");
    });

    /*
     * validate JSON wrapper
     */
    And("^the response is valid JSON$", () -> {
      oDataRawResponse.get().getRawResponse().toString();
      assertTrue(Utils.isValidJson(responseData.get()));

      LOG.info("Response is valid JSON!");
    });
  }

  private static class Utils {
    /**
     * Tests the given string to see if it's valid JSON
     * @param jsonString
     * @return true if valid, false otherwise. Throws {@link IOException}
     */
    private static boolean isValidJson(String jsonString) {
      try {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.readTree(jsonString);
        return true;
      } catch (IOException e) {
        return false;
      }
    }

    /**
     * Resolves the given item into a value
     * @param item an item which can either be a reference to a parameter, client setting, or it can be an actual value.
     * @return the resolved setting or item if it's not a client setting or parameter
     */
    private static String resolveValue(String item, Settings settings) {
      if (item.contains("Parameter_")) {
        return settings.getParameters().getValue(item.replace("Parameter_", ""));
      }

      if (item.contains("ClientSettings_")) {
        return settings.getClientSettings().get(item.replace("ClientSettings_", ""));
      }

      return item;
    }
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
