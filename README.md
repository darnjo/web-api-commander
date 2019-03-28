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
    --bearerToken <b>    the bearer token to be used with the request
    --convertEDMXtoOAI   converts EDMX in inputPath to OAI in place, or to
                         optional outputPath
    --getMetadata        fetches metadata from hostName using bearerToken
    --getURI             executes GET on URI on hostName using bearerToken
    --help               print help
    --serviceRoot <s>    root URL to the Web API Service Root
    
    --inputFile <i>      path to input file
    --outputFile <o>     path to output file
    --readEntities       reads entityName from host using bearerToken
    --validateMetadata   validates metadata in filePath
```

## Examples

1) 

