package org.reso;

import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.format.ContentType;
import org.reso.resoscript.ClientSettings;
import org.reso.resoscript.Request;
import org.reso.resoscript.Settings;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Dictionary;
import java.util.function.Function;

/**
 * Entry point of the RESO Web API Commander, which is a command line OData client that uses the Java Olingo
 * Client Library to handle OData and the Apache CXF library to handle Auth. Currently, the following forms
 * of Auth are supported:
 * <p>
 * - Bearer Tokens
 * <p>
 * Exposes several different actions for working with OData-based WebAPI servers.
 * This application is structured so that the Main class is an OData WebAPI consumer
 * using the Commander class, which contains the actual methods for working with OData.
 * <p>
 * For usage, see README.
 * For documentation, see /doc*
 * <p>
 */
public class Main {

  private static final Logger log = Logger.getLogger(Main.class);
  private static final String DIVIDER = "==============================================================";

  public static void main(String[] params) {

    // create the command line parser
    CommandLineParser parser = new DefaultParser();
    Edm metadata;
    Commander commander;

    String serviceRoot, bearerToken;

    try {
      // parser for command line arguments
      CommandLine cmd = parser.parse(APP_OPTIONS.getOptions(), params);

      serviceRoot = cmd.getOptionValue(APP_OPTIONS.SERVICE_ROOT, null);

      // only bearer token support for now TODO: apache CXF for other forms of auth
      bearerToken = cmd.getOptionValue(APP_OPTIONS.BEARER_TOKEN, null);

      boolean useEdmEnabledClient = cmd.hasOption(APP_OPTIONS.USE_EDM_ENABLED_CLIENT);

      // using the edmEnabledClient requires the serviceRoot for schema validation, which is performed
      // against the payload each time the request is made when enabled.
      if (useEdmEnabledClient && !(cmd.hasOption(APP_OPTIONS.SERVICE_ROOT) || cmd.hasOption(APP_OPTIONS.ACTIONS.RUN_RESOSCRIPT))) {
        printErrorMsgAndExit("\nERROR: --" + APP_OPTIONS.SERVICE_ROOT
            + " is required with the --" + APP_OPTIONS.USE_EDM_ENABLED_CLIENT + " option!");
      }

      // pre-load options for later use
      String inputFile = cmd.getOptionValue(APP_OPTIONS.INPUT_FILE, null);
      String outputFile = cmd.getOptionValue(APP_OPTIONS.OUTPUT_FILE, null);
      String uri = cmd.getOptionValue(APP_OPTIONS.URI, null);
      ContentType contentType = Commander.getContentType(cmd.getOptionValue(APP_OPTIONS.CONTENT_TYPE, null));

      //pass -1 to get all pages, default is 10
      int limit = Integer.parseInt(cmd.getOptionValue(APP_OPTIONS.LIMIT, "10"));

      //if we're running from a RESOScript, load settings and extract the Bearer Token before we continue
      Settings settings = null;
      if (cmd.hasOption(APP_OPTIONS.ACTIONS.RUN_RESOSCRIPT)) {
        log.info(DIVIDER);
        log.info("Web API Commander Starting... Press <ctrl+c> at any time to exit.");

        log.debug("Loading RESOScript: " + inputFile);
        settings = Settings.loadFromRESOScript(new File(inputFile));
        log.debug("RESOScript loaded successfully!");

        log.debug("Setting Commander Bearer Token...");
        bearerToken = settings.getClientSettings().get(ClientSettings.BEARER_TOKEN);
        log.debug("Bearer token loaded... first 4 characters:" + bearerToken.substring(0, 4));

        serviceRoot = settings.getClientSettings().get(ClientSettings.WEB_API_URI);
        log.debug("Service root is: " + serviceRoot);
      }

      // create a new Web API Commander
      commander = new Commander.Builder()
              .serviceRoot(serviceRoot)
              .bearerToken(bearerToken)
              .useEdmEnabledClient(useEdmEnabledClient)
              .build();

      if (cmd.hasOption(APP_OPTIONS.ACTIONS.RUN_RESOSCRIPT)) {
        if (settings == null) {
          log.error("RESOScript option was specified but Settings could not be loaded.");
          log.error("Input File: " + inputFile);
          System.exit(Commander.NOT_OK);
        } else {
          log.info("Running " + settings.getRequests().size() + " Request(s)");
          log.info("RESOScript: " + inputFile);
          log.info(DIVIDER + "\n\n");

          String path = inputFile.replace(".resoscript", "") + "-" + getTimestamp.apply(new Date());
          String resolvedUrl = null;

          int i = 0;
          for (Request request : settings.getRequests()) {
            try {
                log.info("[Test #" + ++i + "]");
                log.info("Test Name: [" + request.getName().replace(".json", "") + "]");

                resolvedUrl = Settings.resolveParameters(request, settings).getUrl();

                log.debug("Resolved URL: " + resolvedUrl);
                commander.saveRawGetRequest(resolvedUrl, path + "/" + request.getOutputFile());
                log.info("Request " + request.getName() + " complete!\n\n");
                //log.info(DIVIDER + "\n\n");
            } catch (Exception ex) {
              log.error("ERROR: exception thrown in RUN_RESOSCRIPT Action. Exception is: \n" + ex.toString());
            }
          }

          log.info(DIVIDER);
          log.info("RESOScript Complete!");
          log.info(DIVIDER + "\n\n");
        }
      } else if (cmd.hasOption(APP_OPTIONS.ACTIONS.GET_METADATA)) {
        APP_OPTIONS.validateAction(cmd, APP_OPTIONS.ACTIONS.GET_METADATA);

        metadata = commander.getMetadata(outputFile);

        log.info("\nThe metadata contains the following items:");
        prettyPrint(metadata);

        log.info("\nChecking Metadata for validity...");
        if (commander.validateMetadata(outputFile)) {
          log.info("==> Valid Metadata!");
        } else {
          log.error("==> Invalid Metadata!");
          System.exit(Commander.NOT_OK);
        }

      } else if (cmd.hasOption(APP_OPTIONS.ACTIONS.VALIDATE_METADATA)) {
        APP_OPTIONS.validateAction(cmd, APP_OPTIONS.ACTIONS.VALIDATE_METADATA);

        /**
         * Validates the metadata in inputFile in three ways:
         *    - de-serializes it into a native Edm object, which will fail if given metadata isn't valid
         *    - verifies whether the given EDMX file is a valid service document
         *    - verifies whether the given EDMX file is in version 4 format
         */
        if (commander.validateMetadata(inputFile)) {
          log.info("Valid Metadata!");
        } else {
          log.error("ERROR: Invalid Metadata!\n");
          System.exit(Commander.NOT_OK);
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
          System.exit(Commander.NOT_OK);
          log.error(ex.toString());
        }

      } else if (cmd.hasOption(APP_OPTIONS.ACTIONS.SAVE_RAW_GET_REQUEST)) {
        APP_OPTIONS.validateAction(cmd, APP_OPTIONS.ACTIONS.SAVE_RAW_GET_REQUEST);

        commander.saveRawGetRequest(uri, outputFile);

      } else if (cmd.hasOption(APP_OPTIONS.ACTIONS.CONVERT_EDMX_TO_OAI)) {
        APP_OPTIONS.validateAction(cmd, APP_OPTIONS.ACTIONS.CONVERT_EDMX_TO_OAI);

        //converts metadata in input source file to output file
        commander.convertMetadata(inputFile);

      } else {
        printHelp(APP_OPTIONS.getOptions());
      }
    } catch (ParseException exp) {
      log.error("\nERROR: Parse Exception, Commander cannot continue! " + exp.getMessage());
      System.exit(Commander.NOT_OK);
    }
  }

  /**
   * Prints an error message and the help for the application, then exits with 1.
   *
   * @param msg the message to print
   */
  private static void printErrorMsgAndExit(String msg) {
    log.error("\n\n" + msg + "\n");
    printHelp(APP_OPTIONS.getOptions());
    System.exit(Commander.NOT_OK);
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
      log.info("\nNamespace: " + schema.getNamespace());
      log.info(DIVIDER);

      schema.getTypeDefinitions().forEach(a ->
          log.info("\tType Definition:" + a.getFullQualifiedName().getFullQualifiedNameAsString()));

      schema.getEnumTypes().forEach(a ->
          log.info("\tEnum Type: " + a.getFullQualifiedName().getFullQualifiedNameAsString()));

      schema.getEntityTypes().forEach(a ->
          log.info("\tEntity Type: " + a.getFullQualifiedName().getFullQualifiedNameAsString()));

      schema.getComplexTypes().forEach(a ->
          log.info("\tComplex Entity Type: " + a.getFullQualifiedName().getFullQualifiedNameAsString()));

      schema.getAnnotationGroups().forEach(a ->
          log.info("\tAnnotations: " + a.getQualifier() + ", Target Path: " + a.getTargetPath()));

      schema.getTerms().forEach(a ->
          log.info(a.getFullQualifiedName().getFullQualifiedNameAsString()));
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
     * Validates options for the various actions exposed in Main.
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

  private static Function<Date, String> getTimestamp = (date) -> {
    DateFormat df = new SimpleDateFormat("yyyyMMddHHMMssS");
    return df.format(date);
  };
}
