package org.reso;


import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.edm.EdmSchema;

public class Main {

  private enum TASK {
    GET_METADATA,
    READ_ENTITIES,
    EXECUTE_URI,
    VALIDATE_METADATA,
    CONVERT_EDMX_TO_OAI
  }

  private static final Logger log = Logger.getLogger(Main.class);

  public static void main(String[] params) {

    String serviceRoot, bearerToken, entityName;

    //working
//    serviceRoot = "https://rets.io/api/v2/OData/har";
//    bearerToken = "887da184c3b60d9d7b80ea975bb1db98";
//    entityName = "Media";
//

//    serviceRoot = "http://rts-api.mlsgrid.com/";
//    bearerToken = "64ed09cc5876671fec76776232213f96fc40d4eb";
//    entityName = "PropertyResi";

    serviceRoot = "http://rapitest.realcomp.com/odata";
    bearerToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ik16aEdRa05GUkRKRk5qYzBRamxHTkVWRE1Ua3lSRUUzUVRsQ09EWTVNMEkyTlRKRFFUVTFNdyJ9.eyJpc3MiOiJodHRwczovL3JlYWxjb21wYXBpLmF1dGgwLmNvbS8iLCJzdWIiOiI4UnozcDIyR084Mkp0cnh4bjJWVVFUV1ltNGN4SXllZkBjbGllbnRzIiwiYXVkIjoicmFwaS5yZWFsY29tcC5jb20iLCJpYXQiOjE1NTIzMjM4NTEsImV4cCI6MTU1MjQxMDI1MSwiYXpwIjoiOFJ6M3AyMkdPODJKdHJ4eG4yVlVRVFdZbTRjeEl5ZWYiLCJndHkiOiJjbGllbnQtY3JlZGVudGlhbHMifQ.lNeMb4KM3Q2exvDMseVfczaakHB3IbiM2_QcrcECWzPtNjTy0q8xQwgBnEZvYfNUERNYkupSj4NXnmJWTW3moB_-L6AFujfn3iSBspLTdUmyV8ffk-NQkeAPKsf5Dhu8fmpsNEeNmZ04-1frZzlbzqx3dDG0rsV85M3VXMvPaGpwc1_wgKv5M2TmGZYRdOw4ASVI3pMElpldygnct3cL6CXbZb0Xq0tmwZhXuv06SMeHqoRyeqaUah5z1UELpVGyxgZK1AfsOGlssJ2iZGUXt47mvdPcsh_u8fA8mW2Y7lQW84y6Nb1Z9rp0L86VNX-Mg5UORCp8r3LfEv7aAMoAZA";
    entityName = "Property";

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


    // create the command line parser
    CommandLineParser parser = new DefaultParser();
    Options options = getOptions();
    Edm metadata;
    Commander commander;

    try {
      // parse the command line arguments
      CommandLine cmd = parser.parse( options, params );

      boolean useEdmEnabledClient = Boolean.parseBoolean(cmd.getOptionValue("useEdmEnabledClient"));

      // create a new Web API Commander instance
      commander = new Commander(serviceRoot, bearerToken, useEdmEnabledClient);

      String inputFile = cmd.getOptionValue("inputFile");
      String outputFile = cmd.getOptionValue("outputFile");
      entityName = cmd.getOptionValue("entityName");

      if (cmd.hasOption("help")) {

        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java -jar web-api-commander", options);

      } else if (cmd.hasOption("getMetadata")) {

        log.info("Getting metadata from " + serviceRoot + "...");
        metadata = commander.getMetadata(outputFile);
        log.info("Metadata file (EDMX) saved to " + outputFile);

        log.info("The metadata contains the following items:");

        prettyPrint(metadata);

      } else if (cmd.hasOption("readEntities")) {

        // pass -1 to get all pages
        int limit = Integer.parseInt(cmd.getOptionValue("limit"));

        //TODO: pass -1 to get all entities
        log.info("Reading entities for the given resource: " + entityName);
        ClientEntitySet entities = commander.readEntities(entityName, limit);

        log.info(entities.getCount() + " entities fetched: ");
        entities.getEntities().stream().forEach(entity -> log.info(entity.toString()));

        log.info("Saving to file: " + outputFile);
        commander.serializeEntitySet(entities, outputFile);

      } else if (cmd.hasOption("validateMetadata")) {

        if (commander.validateMetadata(inputFile)) {
          log.info("Valid Metadata!");
        } else {
          log.error("ERR: Invalid Metadata!");
        }

      } else if (cmd.hasOption("getURI")) {
        try {
          String expr = "https://rets.io/api/v2/OData/har/Property?$top=10&$filter=ListPrice%20gt%201000";
          commander.getEntitySet(expr);
        } catch (Exception ex) {
          log.error(ex.toString());
        }

      } else if (cmd.hasOption("convertEDMXtoOAI")) {
//        works - commander.convertMetadata("./REQ-WA103-END3_Metadata_BKFSWS103_BlackKnight.xml");
//        works - commander.convertMetadata("./REQ-WA103-END3_Metadata_CorLogicTrestle_Gene_CoreLogicTrestle.xml");
//        works - commander.convertMetadata("./REQ-WA103-END3_Metadata_ITechMLS_Rapattoni_Formatted.xml");
//        works - commander.convertMetadata("./REQ-WA103-END3_Metadata_MLSGrid_MLSGrid.xml");
//        works - commander.convertMetadata("./REQ-WA103-END3_Metadata_MLSGrid_MLSGrid_Formatted.xml");
//        works - commander.convertMetadata("./REQ-WA103-END3_Metadata_MLSHawaiiInc_Hawaii_IS.xml");
//        works - commander.convertMetadata("./REQ-WA103-END3_Metadata_NC_362_N_CENTRAL_MS__Navica.xml");
//        works - commander.convertMetadata("./REQ-WA103-END3_Metadata_SomersetLakeCumberla_FBS.xml");
//        works - commander.convertMetadata("./REQ-WA103-END3_Metadata_UREA_Utah Real Estate.xml");

        commander.convertMetadata("./metadata/REQ-WA103-END3_Metadata_UREA_Utah Real Estate.xml");
      }
    } catch( ParseException exp ) {
      System.out.println( "Unexpected exception:" + exp.getMessage() );
    }
  }

  private static void prettyPrint(Edm metadata) {

    metadata.getSchemas().stream().forEach(s -> {
      log.info("\n*** Namespace: " + s.getNamespace());

      s.getTypeDefinitions().stream().forEach(
          t -> log.info("\tType Definition:" + t.getFullQualifiedName().getFullQualifiedNameAsString()));

      s.getEnumTypes().stream().forEach(
          t -> log.info("\tEnum Type: " + t.getFullQualifiedName().getFullQualifiedNameAsString()));

      s.getEntityTypes().stream().forEach(
          t -> log.info("\tEntity Type: " + t.getFullQualifiedName().getFullQualifiedNameAsString()));

      s.getComplexTypes().stream().forEach(
          t -> log.info("\tComplex Entity Type: " + t.getFullQualifiedName().getFullQualifiedNameAsString()));

      s.getAnnotationGroups().stream().forEach(
          a -> log.info("\tAnnotations: " + a.getQualifier()));

      s.getTerms().stream().forEach(t ->
          log.info(t.getFullQualifiedName().getFullQualifiedNameAsString()));
    });
  }


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
      .addOption(Option.builder().argName("m").longOpt("getMetadata").hasArg(false).desc("fetches metadata from hostName using bearerToken").build())
      .addOption(Option.builder().argName("r").longOpt("readEntities").hasArg(false).desc("reads entityName from host using bearerToken").build())
      .addOption(Option.builder().argName("g").longOpt("getURI").hasArg(false).desc("executes GET on URI on hostName using bearerToken").build())
      .addOption(Option.builder().argName("v").longOpt("validateMetadata").hasArg(false).desc("validates metadata in filePath").build())
      .addOption(Option.builder().argName("c").longOpt("convertEDMXtoOAI").hasArg(false).desc("converts EDMX in inputPath to OAI in place, or to optional outputPath").build());

    return new Options()
      .addOption(helpOption)
      .addOption(hostNameOption)
      .addOption(bearerTokenOption)
      .addOption(inputFileOption)
      .addOption(outputFileOption)
      .addOption(limit)
      .addOption(entityName)
      .addOption(useEdmEnabledClient)
      .addOptionGroup(actions);
  }

}
