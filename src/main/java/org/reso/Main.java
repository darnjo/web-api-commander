package org.reso;

import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.commons.api.edm.Edm;

/**
 * Entry point of the RESO Web API Commander
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

    String serviceRoot, bearerToken, entityName, uri;

    try {
      // parse the command line arguments
      CommandLine cmd = parser.parse( options, params );

      //TODO: option name constants

      //either serviceRoot or uri is required
      serviceRoot = cmd.getOptionValue("serviceRoot", null);
      uri = cmd.getOptionValue("uri", null);

      //only bearer token support for now
      //TODO: apache CXF for other forms of auth
      bearerToken = cmd.getOptionValue("bearerToken", null);



      //--overrides begin--//

      //for now allow overriding of parameters from the code below
      //TODO: remove token info and squash before releasing to the public
      if (serviceRoot == null || uri == null || bearerToken == null) {

        //working
        serviceRoot = "https://rets.io/api/v2/OData/har";
        bearerToken = "887da184c3b60d9d7b80ea975bb1db98";
        entityName = "Media";
        uri = "https://rets.io/api/v2/OData/har/Property?$filter=ListPrice%20gt%201000";


        //problem with next links, they don't have skip in the correct place
//    serviceRoot = "http://rts-api.mlsgrid.com/";
//    bearerToken = "64ed09cc5876671fec76776232213f96fc40d4eb";
//    entityName = "PropertyResi";

//    serviceRoot = "http://rapitest.realcomp.com/odata";
//    bearerToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ik16aEdRa05GUkRKRk5qYzBRamxHTkVWRE1Ua3lSRUUzUVRsQ09EWTVNMEkyTlRKRFFUVTFNdyJ9.eyJpc3MiOiJodHRwczovL3JlYWxjb21wYXBpLmF1dGgwLmNvbS8iLCJzdWIiOiI4UnozcDIyR084Mkp0cnh4bjJWVVFUV1ltNGN4SXllZkBjbGllbnRzIiwiYXVkIjoicmFwaS5yZWFsY29tcC5jb20iLCJpYXQiOjE1NTIzMjM4NTEsImV4cCI6MTU1MjQxMDI1MSwiYXpwIjoiOFJ6M3AyMkdPODJKdHJ4eG4yVlVRVFdZbTRjeEl5ZWYiLCJndHkiOiJjbGllbnQtY3JlZGVudGlhbHMifQ.lNeMb4KM3Q2exvDMseVfczaakHB3IbiM2_QcrcECWzPtNjTy0q8xQwgBnEZvYfNUERNYkupSj4NXnmJWTW3moB_-L6AFujfn3iSBspLTdUmyV8ffk-NQkeAPKsf5Dhu8fmpsNEeNmZ04-1frZzlbzqx3dDG0rsV85M3VXMvPaGpwc1_wgKv5M2TmGZYRdOw4ASVI3pMElpldygnct3cL6CXbZb0Xq0tmwZhXuv06SMeHqoRyeqaUah5z1UELpVGyxgZK1AfsOGlssJ2iZGUXt47mvdPcsh_u8fA8mW2Y7lQW84y6Nb1Z9rp0L86VNX-Mg5UORCp8r3LfEv7aAMoAZA";
//    entityName = "Property";

        //not working - bad metadata
//    serviceRoot = "https://rets.io/api/v2/OData/";
//    bearerToken = "fbee0196c78d575b1b81d27d03290a1e";
//    entityName = "Property";

//    serviceRoot = "http://retswebapi.raprets.com/BAK/RESO/odata/";
//    bearerToken = "684b0e328cb4420e9d5bfa119f79164a";

        //not working - bad auth token
//    serviceRoot = "https://api.catylist.com/RESO/OData/";
//    bearerToken = "6ae90a099caace7e808d17819a79234a";

//    serviceRoot = "https://sparkapi.com/Reso/OData";
//    bearerToken = "ay44wtv8dh6dhgweuq045ig2r";
//    entityName = "Property";

      }

      //--overrides end--//


      //using the edmEnabledClient requires the serviceRoot for schema validation, which is performed
      //against the payload each time the request is made when enabled.
      boolean useEdmEnabledClient = Boolean.parseBoolean(cmd.getOptionValue("useEdmEnabledClient"));

      if (serviceRoot == null || uri == null || bearerToken == null) {

        if (useEdmEnabledClient && serviceRoot == null) {
          log.error("ERROR: serviceRoot is required when using the edmEnabledClient!");
        } else if (serviceRoot == null && uri == null){
          log.error("ERROR: serviceRoot or uri are required!");
        }

        log.error("ERROR: bearerToken is required!");
        printHelp(options);
        return;
      }

      // create a new Web API Commander instance
      commander = new Commander(serviceRoot, bearerToken, useEdmEnabledClient);

      String inputFile = cmd.getOptionValue("inputFile");
      String outputFile = cmd.getOptionValue("outputFile");
      entityName = cmd.getOptionValue("entityName");

      if (cmd.hasOption("getMetadata")) {
        if (cmd.hasOption("outputFile")) {
          log.info("Getting metadata from " + serviceRoot + "...");
          metadata = commander.getMetadata(outputFile);
          log.info("Metadata file (EDMX) saved to " + outputFile);

          log.info("The metadata contains the following items:");
          prettyPrint(metadata);
        } else {
          log.error("ERROR: --outputFile is required with the --getMetadata option!");
          printHelp(options);
        }
      } else if (cmd.hasOption("readEntities")) {
        if (cmd.hasOption("entityName") && cmd.hasOption("limit") && cmd.hasOption("outputFile")) {
          // pass -1 to get all pages
          int limit = Integer.parseInt(cmd.getOptionValue("limit"));

          //TODO: pass -1 to get all entities
          log.info("Reading entities for the given resource: " + entityName);
          ClientEntitySet entities = commander.readEntities(entityName, limit);

          log.info(entities.getCount() + " entities fetched: ");
          entities.getEntities().stream().forEach(entity -> log.info(entity.toString()));

          log.info("Saving to file: " + outputFile);
          commander.serializeEntitySet(entities, outputFile);
        } else {
          log.error("ERROR: --entityName, --limit, and --outputFile are required with the --readEntities option!");
          printHelp(options);
        }

      } else if (cmd.hasOption("validateMetadata")) {
        if (cmd.hasOption("inputFile")) {
          if (commander.validateMetadata(inputFile)) {
            log.info("Valid Metadata!");
          } else {
            log.error("ERROR: Invalid Metadata!");
          }
        } else {
          log.error("ERROR: --inputFile is required with the --validateMetadata option!");
          printHelp(options);
        }

      } else if (cmd.hasOption("getEntitySet")) {
        if (cmd.hasOption("uri")) {
          try {
            commander.getEntitySet(uri);
          } catch (Exception ex) {
            log.error(ex.toString());
          }
        } else {
          log.error("ERROR: --uri is required with the --getEntitySet option!");
          printHelp(options);
        }

      } else if (cmd.hasOption("convertEDMXtoOAI")) {
        if (cmd.hasOption("inputFile")) {
          //converts metadata in input source file to output file
          commander.convertMetadata(inputFile);
        } else {
          log.error("ERROR: --inputFile is required with the --convertEDMXtoOAI option!");
          printHelp(options);
        }
      } else {
        printHelp(options);
      }
    } catch( ParseException exp ) {
      log.error( "ERROR: Unexpected exception:" + exp.getMessage() + "!");
    }
  }

  private static void printHelp(Options options) {
    new HelpFormatter().printHelp("java -jar web-api-commander", options);
  }

  /**
   * Metadata Pretty Printer
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
        .argName("e").longOpt("entityName").hasArg().desc("the name of the entity to fetch, e.g. Property").build();

    Option useEdmEnabledClient = Option.builder()
        .argName("d").longOpt("useEdmEnabledClient").hasArg().desc("true if an EdmEnabledClient should be used, false otherwise").build();

    Option helpOption = Option.builder()
      .argName("?").longOpt("help").hasArg(false).desc("print help").build();

    OptionGroup actions = new OptionGroup()
      .addOption(Option.builder().argName("m").longOpt("getMetadata").hasArg(false).desc("fetches metadata from <serviceRoot> using <bearerToken> and saves results in <outputFile>.").build())
      .addOption(Option.builder().argName("r").longOpt("readEntities").hasArg(false).desc("reads <entityName> from <serviceRoot> using <bearerToken> and saves results in <outputFile>.").build())
      .addOption(Option.builder().argName("g").longOpt("getEntitySet").hasArg(false).desc("executes GET on <uri> using the given <serviceRoot> and <bearerToken>.").build())
      .addOption(Option.builder().argName("v").longOpt("validateMetadata").hasArg(false).desc("validates previously-fetched metadata in the <inputFile> path.").build())
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
      .addOptionGroup(actions);
  }

}
