package org.reso;


import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.commons.api.edm.Edm;

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

    String serviceRoot, bearerToken, wellKnownResourceName;

    //working
//    serviceRoot = "https://rets.io/api/v2/OData/har";
//    bearerToken = "887da184c3b60d9d7b80ea975bb1db98";
//    wellKnownResourceName = "Media";

    serviceRoot = "http://rts-api.mlsgrid.com/";
    bearerToken = "64ed09cc5876671fec76776232213f96fc40d4eb";
    wellKnownResourceName = "PropertyResi";

      //not working - bad metadata
//    serviceRoot = "https://rets.io/api/v2/OData/";
//    bearerToken = "fbee0196c78d575b1b81d27d03290a1e";
//    wellKnownResourceName = "Property";

//    serviceRoot = "http://retswebapi.raprets.com/BAK/RESO/odata/";
//    bearerToken = "684b0e328cb4420e9d5bfa119f79164a";

      //not working - bad auth token
//    serviceRoot = "https://api.catylist.com/RESO/OData/";
//    bearerToken = "6ae90a099caace7e808d17819a79234a";

//    serviceRoot = "https://sparkapi.com/Reso/OData";
//    bearerToken = "ay44wtv8dh6dhgweuq045ig2r";
//    wellKnownResourceName = "Property";


    // create the command line parser
    CommandLineParser parser = new DefaultParser();
    Options options = getOptions();

    // create a new Web API Commander instance
    Commander commander = new Commander(serviceRoot, bearerToken);
    Edm metadata;

    try {

      // parse the command line arguments
      CommandLine cmd = parser.parse( options, params );

      if (cmd.hasOption("help")) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("commander", options);
      } else if (cmd.hasOption("getMetadata")) {
        String outputFile = cmd.getOptionValue("outputFile");

        log.info("Getting metadata from " + serviceRoot + "...");

        metadata = commander.getMetadata(outputFile);

        metadata.getSchemas().stream().forEach(s -> {
          log.info("\n*** Namespace: " + s.getNamespace());
          s.getTypeDefinitions().stream().forEach(t -> log.info("\tType Definitions: " + t.getFullQualifiedName().getFullQualifiedNameAsString()));
          s.getEnumTypes().stream().forEach(t -> log.info("\tEnum Type: " + t.getFullQualifiedName().getFullQualifiedNameAsString()));
          s.getEntityTypes().stream().forEach(t -> log.info("\tEntity Type: " + t.getFullQualifiedName().getFullQualifiedNameAsString()));
          s.getComplexTypes().stream().forEach(t -> log.info("\tComplex Entity Type: " + t.getFullQualifiedName().getFullQualifiedNameAsString()));
          s.getAnnotationGroups().stream().forEach(a -> log.info("\tAnnotations: " + a.getQualifier()));
        });

      } else if (cmd.hasOption("readEntities")) {

        ClientEntitySet entities = commander.readEntities(wellKnownResourceName);
        System.out.println(entities.getEntities().stream().map(e -> e.toString()));

      } else if (cmd.hasOption("validateMetadata")) {
        String inputFile = cmd.getOptionValue("inputFile");

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
        String inputFile = cmd.getOptionValue("inputFile");
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


  private static Options getOptions() {
    // create Options
    Option hostNameOption = Option.builder()
      .argName("h").longOpt("hostName").hasArg().desc("root URL to the host").build();

    Option bearerTokenOption = Option.builder()
      .argName("b").longOpt("bearerToken").hasArg().desc("the bearer token to be used with the request").build();

    Option inputFileOption = Option.builder()
      .argName("i").longOpt("inputFile").hasArg().desc("path to input file").build();

    Option outputFileOption = Option.builder()
      .argName("o").longOpt("outputFile").hasArg().desc("path to output file").build();

    Option uriOption = Option.builder()
      .argName("u").longOpt("uri").hasArg().desc("URI for raw request").build();

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
      .addOptionGroup(actions);
  }

}
