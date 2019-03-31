package org.reso;

import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.commons.api.edm.Edm;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Entry point of the RESO Web API Commander, which is a command line OData client that uses the Java Olingo
 * Client Library to handle OData and the Apache CXF library to handle Auth. Currently, the following forms
 * of Auth are supported:
 *
 *  - Bearer Tokens
 *
 * Exposes several different actions for working with OData-based WebAPI servers.
 * This application is structured so that the Main class is an OData WebAPI consumer
 * using the Commander class, which contains the actual methods for working with OData.
 *
 * For usage, see README.
 *
 * TODO: add better handling for required parameters, currently just checks if they're there and prints help if not
 */
public class Main {

  private static final Logger log = Logger.getLogger(Main.class);

  public static void main(String[] params) {

    // create the command line parser
    CommandLineParser parser = new DefaultParser();
    Options options = getOptions();
    Edm metadata;
    Commander commander;

    String serviceRoot, bearerToken;

    try {
      // parse the command line arguments
      CommandLine cmd = parser.parse(options, params);

      // TODO: add constants for option names
      serviceRoot = cmd.getOptionValue("serviceRoot", null);

      // only bearer token support for now
      // TODO: apache CXF for other forms of auth
      bearerToken = cmd.getOptionValue("bearerToken", null);

      // using the edmEnabledClient requires the serviceRoot for schema validation, which is performed
      // against the payload each time the request is made when enabled.
      boolean useEdmEnabledClient = cmd.hasOption("useEdmEnabledClient");

      if (useEdmEnabledClient && !cmd.hasOption("serviceRoot")) {
        log.error("\nERROR: --serviceRoot is required with the --useEdmEnabledClient option!");
        printHelp(options);
        System.exit(1);
      }

      // create a new Web API Commander instance
      commander = new Commander(serviceRoot, bearerToken, useEdmEnabledClient);

      // pre-load options for later use
      String inputFile = cmd.getOptionValue("inputFile", null);
      String outputFile = cmd.getOptionValue("outputFile", null);
      String entityName = cmd.getOptionValue("entityName", null);
      String uri = cmd.getOptionValue("uri", null);
      String filter = cmd.getOptionValue("filter", null);

      //pass -1 to get all pages, default is 10
      int limit = Integer.parseInt(cmd.getOptionValue("limit", "10"));

      if (cmd.hasOption("getMetadata")) {

        /**
         * Gets metadata from the given serviceRoot and write it to the given outputFile.
         * Calls the metadata validator on all metadata that's been consumed, but doesn't error
         * if the metadata is invalid, rather it fetches it and analyzes it afterwards.
         */
        if (cmd.hasOption("serviceRoot") && cmd.hasOption("bearerToken") && cmd.hasOption("outputFile")) {
          log.info("\nGetting metadata from " + serviceRoot + "...");
          metadata = commander.getMetadata(outputFile);

          log.info("\nThe metadata contains the following items:");
          prettyPrint(metadata);

          log.info("\nChecking Metadata for validity...");
          if (commander.validateMetadata(outputFile)) {
            log.info("--> Valid Metadata!");
          } else {
            log.error("--> Invalid Metadata!");
            System.exit(1);
          }

        } else {
          //TODO: handle parameter requirements in a better way.
          //TODO: currently the Command object allows for required/optional params, but not across different actions.
          StringBuilder errSb =
              new StringBuilder("\nERROR: the following options are required when using getMetadata!");

          if (!cmd.hasOption("serviceRoot")) {
            errSb.append("\n\t--serviceRoot");
          }

          if (!cmd.hasOption("bearerToken")) {
            errSb.append("\n\t--bearerToken");
          }

          if (!cmd.hasOption("outputFile")) {
            errSb.append("\n\t--outputFile");
          }

          log.error(errSb.toString() + "\n");
          printHelp(options);
        }

      } else if (cmd.hasOption("validateMetadata")) {

        /**
         * Validates the metadata in inputFile in three ways:
         *    - deserializes it into a native Edm object, which will fail if given metadata isn't valid
         *    - verifies whether the given EDMX file is a valid service document
         *    - verifies whether the given EDMX file is in version 4 format
         *
         *    TODO: add more validity checks, where relevant
         */

        if (cmd.hasOption("inputFile")) {
          if (commander.validateMetadata(inputFile)) {
            log.info("Valid Metadata!");
          } else {
            log.error("\nERROR: Invalid Metadata!");
          }
        } else {
          StringBuilder errSb =
              new StringBuilder("\nERROR: the following options are required when using validateMetadata!");

          if (!cmd.hasOption("serviceRoot")) {
            errSb.append("\n\t--serviceRoot");
          }
          log.error("\nERROR: --inputFile is required with the --validateMetadata option!");
          printHelp(options);
        }

      } else if (cmd.hasOption("getEntitySet")) {

        /**
         * Gets a ClientEntitySet from the given uri. If the useEdmEnabledClient option was passed,
         * then serviceRoot is required, and results fetched from uri are validated against the server's
         * published metadata.
         *
         * Results are written to outputFile.
         */

        if (cmd.hasOption("uri") && cmd.hasOption("bearerToken") && cmd.hasOption("outputFile")) {
          try {
            ClientEntitySet results = commander.getEntitySet(URI.create(uri));

            if (results != null) {
              commander.serializeEntitySet(results, outputFile);
            }
          } catch (Exception ex) {
            log.error(ex.toString());
          }
        } else {
          StringBuilder errSb =
              new StringBuilder("\nERROR: the following options are required when using getEntitySet!");

          if (!cmd.hasOption("serviceRoot")) {
            errSb.append("\n\t--serviceRoot");
          }

          if (!cmd.hasOption("bearerToken")) {
            errSb.append("\n\t--bearerToken");
          }

          if (!cmd.hasOption("uri")) {
            errSb.append("\n\t--uri");
          }

          if (!cmd.hasOption("outputFile")) {
            errSb.append("\n\t--outputFile");
          }

          log.error(errSb.toString());
          printHelp(options);
        }

      } else if (cmd.hasOption("readEntities")) {

        /**
         * Uses the OData
         */

        if (cmd.hasOption("serviceRoot") && cmd.hasOption("bearerToken") && cmd.hasOption("entityName")
            && cmd.hasOption("limit") && cmd.hasOption("outputFile")) {

          //NOTE: pass -1 to get all entities
          log.info("Reading entities for the given resource: " + entityName);
          ClientEntitySet entities = commander.readEntities(entityName, filter, limit);

          log.info(entities.getCount() + " entities fetched: ");
          entities.getEntities().stream().forEach(entity -> log.info(entity.toString()));

          log.info("Saving to file: " + outputFile);
          commander.serializeEntitySet(entities, outputFile);
        } else {
          StringBuilder errSb =
              new StringBuilder("\nERROR: the following options are required when using readEntities!");

          if (!cmd.hasOption("serviceRoot")) {
            errSb.append("\n\t--serviceRoot");
          }

          if (!cmd.hasOption("bearerToken")) {
            errSb.append("\n\t--bearerToken");
          }

          if (!cmd.hasOption("entityName")) {
            errSb.append("\n\t--entityName");
          }

          if (!cmd.hasOption("limit")) {
            errSb.append("\n\t--limit");
          }

          if (!cmd.hasOption("outputFile")) {
            errSb.append("\n\t--outputFile");
          }

          log.error(errSb.toString());
          printHelp(options);
        }

      } else if (cmd.hasOption("saveRawGetRequest")) {
        if (cmd.hasOption("serviceRoot") && cmd.hasOption("bearerToken")
            && cmd.hasOption("uri") && cmd.hasOption("outputFile")) {
          commander.saveRawGetRequest(URI.create(uri), outputFile);
        } else {
          StringBuilder errSb =
              new StringBuilder("\nERROR: the following options are required when using saveRawResponse!");

          if (!cmd.hasOption("serviceRoot")) {
            errSb.append("\n\t--serviceRoot");
          }

          if (!cmd.hasOption("bearerToken")) {
            errSb.append("\n\t--bearerToken");
          }

          if (!cmd.hasOption("outputFile")) {
            errSb.append("\n\t--outputFile");
          }
          log.error(errSb.toString());
          printHelp(options);
        }


      } else if (cmd.hasOption("convertEDMXtoOAI")) {
        if (cmd.hasOption("inputFile")) {
          //converts metadata in input source file to output file
          commander.convertMetadata(inputFile);
        } else {
          StringBuilder errSb =
              new StringBuilder("\nERROR: the following options are required when using convertEDMXtoOAI!");

          if (!cmd.hasOption("inputFile")) {
            errSb.append("\n\t--inputFile");
          }

          log.error(errSb.toString());
          printHelp(options);
        }
      } else {
        printHelp(options);
      }
    } catch (ParseException exp) {
      log.error("\nERROR: Unexpected exception:" + exp.getMessage() + "!");
    }
  }

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
    metadata.getSchemas().stream().forEach(schema -> {
      log.info("\nNamespace: " + schema.getNamespace());
      log.info("=============================================================");

      schema.getTypeDefinitions().stream().forEach(a ->
          log.info("\tType Definition:" + a.getFullQualifiedName().getFullQualifiedNameAsString()));

      schema.getEnumTypes().stream().forEach(a ->
          log.info("\tEnum Type: " + a.getFullQualifiedName().getFullQualifiedNameAsString()));

      schema.getEntityTypes().stream().forEach(a ->
          log.info("\tEntity Type: " + a.getFullQualifiedName().getFullQualifiedNameAsString()));

      schema.getComplexTypes().stream().forEach(a ->
          log.info("\tComplex Entity Type: " + a.getFullQualifiedName().getFullQualifiedNameAsString()));

      schema.getAnnotationGroups().stream().forEach(a ->
          log.info("\tAnnotations: " + a.getQualifier() + ", Target Path: " + a.getTargetPath()));

      schema.getTerms().stream().forEach(a ->
          log.info(a.getFullQualifiedName().getFullQualifiedNameAsString()));
    });
  }

  /**
   * Options helper
   *
   * @return options allowed for command line input
   */
  private static Options getOptions() {
    // create Options
    Option hostNameOption = Option.builder()
        .argName("s").longOpt("serviceRoot").hasArg().desc("service root URL on the host").build();

    Option bearerTokenOption = Option.builder()
        .argName("b").longOpt("bearerToken").hasArg().desc("the bearer token to be used with the request").build();

    Option inputFileOption = Option.builder()
        .argName("i").longOpt("inputFile").hasArg().desc("path to input file").build();

    Option outputFileOption = Option.builder()
        .argName("o").longOpt("outputFile").hasArg().desc("path to output file").build();

    Option uriOption = Option.builder()
        .argName("u").longOpt("uri").hasArg().desc("URI for raw request").build();

    Option limit = Option.builder()
        .argName("l").longOpt("limit").hasArg().desc("the number of records to fetch, or -1 to fetch all").build();

    Option entityName = Option.builder()
        .argName("n").longOpt("entityName").hasArg().desc("the name of the entity to fetch, e.g. Property").build();

    Option useEdmEnabledClient = Option.builder()
        .argName("e").longOpt("useEdmEnabledClient").desc("present if an EdmEnabledClient should be used.").build();

    Option helpOption = Option.builder()
        .argName("?").longOpt("help").hasArg(false).desc("print help").build();

    Option filterOption = Option.builder()
        .argName("f").longOpt("filter").hasArg().desc("if <filter> is passed, then readEntities will use it").build();

    OptionGroup actions = new OptionGroup()
        .addOption(Option.builder().argName("m").longOpt("getMetadata").hasArg(false).desc("fetches metadata from <serviceRoot> using <bearerToken> and saves results in <outputFile>.").build())
        .addOption(Option.builder().argName("r").longOpt("readEntities").hasArg(false).desc("reads <entityName> from <serviceRoot> using <bearerToken> and saves results in <outputFile>.").build())
        .addOption(Option.builder().argName("g").longOpt("getEntitySet").hasArg(false).desc("executes GET on <uri> using the given <serviceRoot> and <bearerToken>.").build())
        .addOption(Option.builder().argName("v").longOpt("validateMetadata").hasArg(false).desc("validates previously-fetched metadata in the <inputFile> path.").build())
        .addOption(Option.builder().argName("w").longOpt("saveRawGetRequest").hasArg(false).desc("performs GET from <requestURI> and saves output to <outputFile>.").build())
        .addOption(Option.builder().argName("c").longOpt("convertEDMXtoOAI").hasArg(false).desc("converts EDMX in <inputFile> to OAI, saving it in <inputFile>.swagger.json").build());

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
        .addOption(filterOption)
        .addOptionGroup(actions);
  }
}
