# RESO Web API Commander

The RESO Web API Commander is a command line Java application that uses
the Apache Olingo library to provide the following functionality:

* Get Metadata
* Validate Metadata
* Read Entities
* Get Data from a Web API URL
* Convert EDMX to Swagger / OAI

The Web API Commander currently supports Bearer Tokens for authentication. 
Additional methods of authentication will be added through subsequent updates.

Help is available from the command line by passing `--help`, which displays
the following information:

```
usage: java -jar web-api-commander
    --bearerToken <b>           the bearer token to be used with the
                                request
    --convertEDMXtoOAI          converts EDMX in <inputFile> to OAI,
                                saving it in <inputFile>.swagger.json
    --entityName <e>            the name of the entity to fetch, e.g.
                                Property
    --getEntitySet              executes GET on <uri> using the given
                                <serviceRoot> and <bearerToken>.
    --getMetadata               fetches metadata from <serviceRoot> using
                                <bearerToken> and saves results in
                                <outputFile>.
    --help                      print help
    --inputFile <i>             path to input file
    --limit <l>                 the number of records to fetch, or -1 to
                                fetch all
    --outputFile <o>            path to output file
    --readEntities              reads <entityName> from <serviceRoot>
                                using <bearerToken> and saves results in
                                <outputFile>.
    --serviceRoot <s>           service root URL on the host
    --uri <u>                   URI for raw request
    --useEdmEnabledClient <d>   true if an EdmEnabledClient should be
                                used, false otherwise
    --validateMetadata          validates previously-fetched metadata in
                                the <inputFile> path.

```

## Usage

###1. Getting Metadata


To get metadata, use the `--getMetadata` argument with the following options:

```
java -jar web-api-commander --getMetadata --serviceRoot <s> --bearerToken <b> --outputFile <o>
```

where `serviceRoot` is the path to the root of the OData WebAPI server.

Assuming everything goes well, metadata will be retrieved from the host and written to the provided `--outputFilename`.

*Note:* additional validation is done after metadata have been received. Errors in metadata 
won't cause the program to terminate, but validation information will be displayed.

<br/> 

###2. Validating Metadata stored in an EDMX file
Sometimes it's useful to be able to validate an already-downloaded EDMX file. Since parsing EDMX 
is an incremental process, validation terminates _each time_ invalid items are encountered. Therefore,
the workflow for correcting an EDMX document that contains errors would be to run the Commander 
repeatedly, fixing errors that are encountered along the way.

To use the validator, call the Web API Commander with the following options:

```
java -jar web-api-commander --validateMetadata --inputFile <i>
```

where `inputFile` is the path to your EDMX file. Errors will be logged according to the `log4j.properties` file
used at runtime. 

<br />

###3. Getting results from a given `uri`


<br />

###4. Getting results with `serviceRoot`, `entityName`, and `limit`


<br />
 
###5. Converting EDMX to Open API definitions in Swagger 2.0 format


