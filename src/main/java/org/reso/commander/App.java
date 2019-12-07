package org.reso.commander;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.format.ContentType;
import org.reso.resoscript.*;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.function.Function;

//TODO: move System.exit() logic from Commander to here
import static org.reso.commander.Commander.NOT_OK;

/**
 * Entry point of the RESO Web API Commander, which is a command line OData client that uses the Java Olingo
 * Client Library to handle OData and the Apache CXF library to handle Auth. Currently, the following forms
 * of Auth are supported:
 * <p>
 * - Bearer Tokens
 * <p>
 * Exposes several different actions for working with OData-based WebAPI servers.
 * This application is structured so that the App class is an OData WebAPI consumer
 * using the Commander class, which contains the actual methods for working with OData.
 * <p>
 * For usage, see README.
 * For documentation, see /docs
 * <p>
 */
public class App {

  private static final Logger LOG = LogManager.getLogger(App.class);
  private static final String DIVIDER = "==============================================================";

  public static void main(String[] params) {
    // create the command line parser
    CommandLineParser parser = new org.apache.commons.cli.DefaultParser();
    Edm metadata = null;

    //available Commander variables
    String serviceRoot = null, bearerToken = null, clientId = null, clientSecret = null,
        authorizationUri = null, tokenUri = null, redirectUri = null, scope = null;
    String inputFilename, outputFile, uri;
    boolean useEdmEnabledClient;
    int limit;

    //created with the commanderBuilder throughout the initialization body
    Commander commander;

    //create a new Commander builder to be used throughout the initialization process
    Commander.Builder commanderBuilder = new Commander.Builder();

    try {
      // parser for command line arguments
      CommandLine cmd = parser.parse(APP_OPTIONS.getOptions(), params);

      // pre-load command line options for later use //
      useEdmEnabledClient = cmd.hasOption(APP_OPTIONS.USE_EDM_ENABLED_CLIENT);
      inputFilename = cmd.getOptionValue(APP_OPTIONS.INPUT_FILE, null);
      outputFile = cmd.getOptionValue(APP_OPTIONS.OUTPUT_FILE, null);
      uri = cmd.getOptionValue(APP_OPTIONS.URI, null);
      ContentType contentType = Commander.getContentType(cmd.getOptionValue(APP_OPTIONS.CONTENT_TYPE, null));
      limit = Integer.parseInt(cmd.getOptionValue(APP_OPTIONS.LIMIT, "10")); //pass -1 to get all pages

      // using the edmEnabledClient requires the serviceRoot for schema validation, which is performed
      // against the payload each time the request is made when enabled.
      if (useEdmEnabledClient && !(cmd.hasOption(APP_OPTIONS.SERVICE_ROOT) || cmd.hasOption(APP_OPTIONS.ACTIONS.RUN_RESOSCRIPT))) {
        printErrorMsgAndExit("\nERROR: --" + APP_OPTIONS.SERVICE_ROOT + " is required with the --" + APP_OPTIONS.USE_EDM_ENABLED_CLIENT + " option!");
      }

      //if we're running a batch, initialize variables from the settings file rather than from command line options
      Settings settings = null;
      if (cmd.hasOption(APP_OPTIONS.ACTIONS.RUN_RESOSCRIPT)) {
        LOG.debug("Loading RESOScript: " + inputFilename);
        settings = Settings.loadFromRESOScript(new File(inputFilename));

        if (settings == null) {
          LOG.error("RESOScript option was specified but Settings could not be loaded.");
          LOG.error("Input File: " + inputFilename);
          System.exit(NOT_OK);
        }

        LOG.debug("RESOScript loaded successfully!");

        serviceRoot = settings.getClientSettings().get(ClientSettings.WEB_API_URI);
        LOG.debug("Service Root is:" + serviceRoot);

        //TODO: add base64 un-encode when applicable
        bearerToken = settings.getClientSettings().get(ClientSettings.BEARER_TOKEN);
        if (bearerToken != null && bearerToken.length() > 0) {
          LOG.debug("Bearer token loaded... first 4 characters:" + bearerToken.substring(0, 4));
        }

        clientId = settings.getClientSettings().get(ClientSettings.CLIENT_IDENTIFICATION);
        clientSecret = settings.getClientSettings().get(ClientSettings.CLIENT_SECRET);
        authorizationUri = settings.getClientSettings().get(ClientSettings.AUTHORIZATION_URI);
        tokenUri = settings.getClientSettings().get(ClientSettings.TOKEN_URI);
        redirectUri = settings.getClientSettings().get(ClientSettings.REDIRECT_URI);
        scope = settings.getClientSettings().get(ClientSettings.CLIENT_SCOPE);

        LOG.debug("Service root is: " + serviceRoot);
      } else {
        //otherwise, load from command line
        serviceRoot = cmd.getOptionValue(APP_OPTIONS.SERVICE_ROOT, null);
        bearerToken = cmd.getOptionValue(APP_OPTIONS.BEARER_TOKEN, null);
      }

      //create Commander instance
      commander = commanderBuilder
          .clientId(clientId)
          .clientSecret(clientSecret)
          .authorizationUri(authorizationUri)
          .tokenUri(tokenUri)
          .redirectUri(redirectUri)
          .scope(scope)
          .serviceRoot(serviceRoot)
          .bearerToken(bearerToken)
          .build();

      //If the RESOScript option was passed, then the correct commander instance should exist at this point
      if (cmd.hasOption(APP_OPTIONS.ACTIONS.RUN_RESOSCRIPT)) {
        int numRequests = settings.getRequests().size();

        LOG.info(DIVIDER);
        LOG.info("Web API Commander Starting... Press <ctrl+c> at any time to exit.");
        LOG.info(DIVIDER);

        LOG.info("Running " + numRequests + " Request(s)");
        LOG.info("RESOScript: " + inputFilename);
        LOG.info(DIVIDER + "\n\n");

        //put in local directory rather than relative to where the input file is
        String directoryName = System.getProperty("user.dir"),
            path = inputFilename
            .substring(inputFilename.lastIndexOf(File.separator), inputFilename.length())
            .replace(".resoscript", "") + "-" + getTimestamp(new Date());
        String resolvedUrl = null;

        Request request;
        for (int i = 0; i < numRequests; i++) {
          try {
            request = settings.getRequests().get(i);

            //TODO: create dynamic JUnit (or similar) test runner
            LOG.info("Test: #" + (i+1));
            LOG.info("Test Name: " + request.getName().replace(".json", ""));

            resolvedUrl = Settings.resolveParameters(request, settings).getUrl();

            LOG.debug("Resolved URL: " + resolvedUrl);

            //output the result of the request to the resolved URL to the output file
            commander.saveGetRequest(resolvedUrl, directoryName + path + File.separator + request.getOutputFile());

            LOG.info("Request " + request.getName() + " complete!\n\n");
          } catch (Exception ex) {
            LOG.error("ERROR: exception thrown in RUN_RESOSCRIPT Action. Exception is: \n" + ex.toString());
          }
        }

        LOG.info(DIVIDER);
        LOG.info("RESOScript Complete!");
        LOG.info(DIVIDER + "\n\n");
        System.exit(0); //terminate the program after the batch completes
      }


      /* otherwise, not a batch request...
         proceed with the selected command-line option */

      if (cmd.hasOption(APP_OPTIONS.ACTIONS.GET_METADATA)) {
        APP_OPTIONS.validateAction(cmd, APP_OPTIONS.ACTIONS.GET_METADATA);

        metadata = commander.getMetadata();

        LOG.info("\nThe metadata contains the following items:");
        prettyPrint(metadata);

        LOG.info("\nChecking Metadata for validity...");
        if (commander.validateMetadata(outputFile)) {
          LOG.info("==> Valid Metadata!");
        } else {
          LOG.error("==> Invalid Metadata!");
          System.exit(NOT_OK);
        }

      } else if (cmd.hasOption(APP_OPTIONS.ACTIONS.VALIDATE_METADATA)) {
        commander = new Commander.Builder().build();
        APP_OPTIONS.validateAction(cmd, APP_OPTIONS.ACTIONS.VALIDATE_METADATA);

        /**
         * Validates the metadata in inputFilename in three ways:
         *    - de-serializes it into a native Edm object, which will fail if given metadata isn't valid
         *    - verifies whether the given EDMX file is a valid service document
         *    - verifies whether the given EDMX file is in version 4 format
         */
        if (commander.validateMetadata(inputFilename)) {
          LOG.info("Valid Metadata!");
        } else {
          LOG.error("ERROR: Invalid Metadata!\n");
        }
      } else if (cmd.hasOption(APP_OPTIONS.ACTIONS.GET_ENTITIES)) {
        APP_OPTIONS.validateAction(cmd, APP_OPTIONS.ACTIONS.GET_ENTITIES);

        //function delegate to handle updating the status
        final Function<Integer, Void> updateStatus = count -> {
          System.out.print("|" + count.toString());
          return null;
        };

        /**
         * Gets a ClientEntitySet from the given uri. If the useEdmEnabledClient option was passed,
         * then serviceRoot is required, and results fetched from uri are validated against the server's
         * published metadata.
         *status
         * Results are written to outputFile.
         */
        try {
          ClientEntitySet results = commander.getEntitySet(uri, limit, updateStatus);

          if (results != null) {
            commander.serializeEntitySet(results, outputFile, contentType);
          }
        } catch (Exception ex) {
          System.exit(NOT_OK);
          LOG.error(ex.toString());
        }

      } else if (cmd.hasOption(APP_OPTIONS.ACTIONS.SAVE_RAW_GET_REQUEST)) {
        APP_OPTIONS.validateAction(cmd, APP_OPTIONS.ACTIONS.SAVE_RAW_GET_REQUEST);

        commander.saveGetRequest(uri, outputFile);

      } else if (cmd.hasOption(APP_OPTIONS.ACTIONS.CONVERT_EDMX_TO_OAI)) {
        APP_OPTIONS.validateAction(cmd, APP_OPTIONS.ACTIONS.CONVERT_EDMX_TO_OAI);

        //converts metadata in input source file to output file
        commander.convertEDMXToSwagger(inputFilename);

      } else {
        printHelp(APP_OPTIONS.getOptions());
      }
    } catch (ParseException exp) {
      LOG.error("\nERROR: Parse Exception, Commander cannot continue! " + exp.getMessage());
      System.exit(NOT_OK);
    }
  }

  /**
   * Prints an error message and the help for the application, then exits with 1.
   *
   * @param msg the message to print
   */
  private static void printErrorMsgAndExit(String msg) {
    LOG.error("\n\n" + msg + "\n");
    printHelp(APP_OPTIONS.getOptions());
    System.exit(NOT_OK);
  }

  /**
   * Prints help
   *
   * @param options the options to print help for.
   */
  private static void printHelp(Options options) {
    new HelpFormatter().printHelp("java -jar web-api-commander", options);
  }

  /**
   * Metadata Pretty Printer
   *
   * @param metadata any metadata in Edm format
   */
  private static void prettyPrint(Edm metadata) {

    //Note: other treatments may be added to this summary info
    metadata.getSchemas().forEach(schema -> {
      LOG.info("\nNamespace: " + schema.getNamespace());
      LOG.info(DIVIDER);

      schema.getTypeDefinitions().forEach(a ->
          LOG.info("\tType Definition:" + a.getFullQualifiedName().getFullQualifiedNameAsString()));

      schema.getEnumTypes().forEach(a ->
          LOG.info("\tEnum Type: " + a.getFullQualifiedName().getFullQualifiedNameAsString()));

      schema.getEntityTypes().forEach(a ->
          LOG.info("\tEntity Type: " + a.getFullQualifiedName().getFullQualifiedNameAsString()));

      schema.getComplexTypes().forEach(a ->
          LOG.info("\tComplex Entity Type: " + a.getFullQualifiedName().getFullQualifiedNameAsString()));

      schema.getAnnotationGroups().forEach(a ->
          LOG.info("\tAnnotations: " + a.getQualifier() + ", Target Path: " + a.getTargetPath()));

      schema.getTerms().forEach(a ->
          LOG.info(a.getFullQualifiedName().getFullQualifiedNameAsString()));
    });
  }

  /**
   * Helps with various app options used by the main application when processing input from the command line.
   */
  private static class APP_OPTIONS {

    //parameter names
    static String SERVICE_ROOT = "serviceRoot";
    static String BEARER_TOKEN = "bearerToken";
    static String INPUT_FILE = "inputFile";
    static String OUTPUT_FILE = "outputFile";
    static String URI = "uri";
    static String LIMIT = "limit";
    static String ENTITY_NAME = "entityName";
    static String USE_EDM_ENABLED_CLIENT = "useEdmEnabledClient";
    static String CONTENT_TYPE = "contentType";
    static String HELP = "help";

    static class ACTIONS {
      //actions
      static String RUN_RESOSCRIPT = "runRESOScript";
      static String GET_METADATA = "getMetadata";
      static String VALIDATE_METADATA = "validateMetadata";
      static String GET_ENTITIES = "getEntities";
      static String SAVE_RAW_GET_REQUEST = "saveRawGetRequest";
      static String CONVERT_EDMX_TO_OAI = "convertEDMXtoOAI";
    }

    /**
     * Validates options for the various actions exposed in App.
     * <p>
     * TODO: determine if there's a way this can be handled using Commons Command.
     *
     * @param cmd    a reference to the current Command instance
     * @param action one of APP_OPTIONS.ACTIONS, representing the action to be performed
     *               <p>
     *               If the given action doesn't validate, then an error message will be printed and the application will exit.
     */
     static void validateAction(CommandLine cmd, String action) {
      String validationResponse = null;

      if (action.matches(ACTIONS.RUN_RESOSCRIPT)) {
        validationResponse = validateOptions(cmd, INPUT_FILE);
      } else if (action.matches(ACTIONS.GET_METADATA)) {
        validationResponse = validateOptions(cmd, SERVICE_ROOT, BEARER_TOKEN, OUTPUT_FILE);
      } else if (action.matches(ACTIONS.VALIDATE_METADATA)) {
        validationResponse = validateOptions(cmd, INPUT_FILE);
      } else if (action.matches(ACTIONS.GET_ENTITIES)) {
        validationResponse = validateOptions(cmd, BEARER_TOKEN, URI, OUTPUT_FILE);
      } else if (action.matches(ACTIONS.SAVE_RAW_GET_REQUEST)) {
        validationResponse = validateOptions(cmd, BEARER_TOKEN, URI, OUTPUT_FILE);
      } else if (action.matches(ACTIONS.CONVERT_EDMX_TO_OAI)) {
        validationResponse = validateOptions(cmd, INPUT_FILE);
      }

      if (validationResponse != null) {
        printErrorMsgAndExit("ERROR: the following options are required when using " + action
            + "\n" + validationResponse + "\n\n");
      }
    }

    /**
     * Validates options passed to the command line
     *
     * @param cmd     a reference to the command line instance
     * @param options a list of options to validate
     * @return an error string containing a formatted message when validation fails, otherwise null (valid)
     */
    static String validateOptions(CommandLine cmd, String... options) {
      StringBuilder sb = new StringBuilder();
      Arrays.stream(options).forEach(option -> {
        if (!cmd.hasOption(option)) {
          sb.append("\n\t --");
          sb.append(option);
          sb.append(" is required!");
        }
      });
      return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * Gets the set of supported application options.
     *
     * @return the options to be used within the application.
     */
    static Options getOptions() {
      // create Options
      Option hostNameOption = Option.builder()
          .argName("s").longOpt(SERVICE_ROOT).hasArg()
          .desc("Service root URL on the host.")
          .build();

      Option bearerTokenOption = Option.builder()
          .argName("b").longOpt(BEARER_TOKEN).hasArg()
          .desc("Bearer token to be used with the request.")
          .build();

      Option inputFileOption = Option.builder()
          .argName("i").longOpt(INPUT_FILE).hasArg()
          .desc("Path to input file.")
          .build();

      Option outputFileOption = Option.builder()
          .argName("o").longOpt(OUTPUT_FILE).hasArg()
          .desc("Path to output file.")
          .build();

      Option uriOption = Option.builder()
          .argName("u").longOpt(URI).hasArg()
          .desc("URI for raw request. Use 'single quotes' to enclose.")
          .build();

      Option limit = Option.builder()
          .argName("l").longOpt(LIMIT).hasArg()
          .desc("The number of records to fetch, or -1 to fetch all.")
          .build();

      Option entityName = Option.builder()
          .argName("n").longOpt(ENTITY_NAME).hasArg()
          .desc("The name of the entity to fetch, e.g. Property.")
          .build();

      Option contentType = Option.builder()
          .argName("t").longOpt(CONTENT_TYPE).hasArg()
          .desc("Results format: JSON (default), JSON_NO_METADATA, JSON_FULL_METADATA, XML.")
          .build();

      Option useEdmEnabledClient = Option.builder()
          .argName("e").longOpt(USE_EDM_ENABLED_CLIENT)
          .desc("present if an EdmEnabledClient should be used.")
          .build();

      Option helpOption = Option.builder()
          .argName("?").longOpt(HELP).hasArg(false)
          .desc("print help")
          .build();

      OptionGroup actions = new OptionGroup()
          .addOption(Option.builder().argName("r").longOpt(ACTIONS.RUN_RESOSCRIPT)
              .desc("Runs commands in RESOScript file given as <inputFile>.").build())
          .addOption(Option.builder().argName("m").longOpt(ACTIONS.GET_METADATA)
              .desc("Fetches metadata from <serviceRoot> using <bearerToken> and saves results in <outputFile>.").build())
          .addOption(Option.builder().argName("g").longOpt(ACTIONS.GET_ENTITIES)
              .desc("Executes GET on <uri> using the given <bearerToken> and optional <serviceRoot> when " +
                  "--useEdmEnabledClient is specified. Optionally takes a <limit>, which will fetch that number " +
                      "of results. Pass --limit -1 to fetch all results.").build())
          .addOption(Option.builder().argName("v").longOpt(ACTIONS.VALIDATE_METADATA)
              .desc("Validates previously-fetched metadata in the <inputFile> path.").build())
          .addOption(Option.builder().argName("w").longOpt(ACTIONS.SAVE_RAW_GET_REQUEST)
              .desc("Performs GET from <requestURI> using the given <bearerToken> and saves output to <outputFile>.").build())
          .addOption(Option.builder().argName("c").longOpt(ACTIONS.CONVERT_EDMX_TO_OAI)
              .desc("Converts EDMX in <inputFile> to OAI, saving it in <inputFile>.swagger.json").build());

      return new Options()
          .addOption(helpOption)
          .addOption(hostNameOption)
          .addOption(bearerTokenOption)
          .addOption(inputFileOption)
          .addOption(outputFileOption)
          .addOption(limit)
          .addOption(entityName)
          .addOption(useEdmEnabledClient)
          .addOption(uriOption)
          .addOption(contentType)
          .addOptionGroup(actions);
    }
  }

  /**
   * Gets a formatted date string for the given date.
   * @param date the date to format
   * @return date string in yyyyMMddHHMMssS format
   */
  private static String getTimestamp(Date date) {
    DateFormat df = new SimpleDateFormat("yyyyMMddHHMMssS");
    return df.format(date);
  }
}
