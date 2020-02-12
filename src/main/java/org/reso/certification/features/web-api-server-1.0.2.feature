Feature: Web API Server 1.0.2 Certification
  All Scenarios Passing means that the Web API server is fully-compliant with the RESO specification.
  It's not expected that a server will pass all scenarios.

  Background:
    Given a RESOScript file was provided
    And Client Settings and Parameters were read from the file
    And an OData client was successfully created from the given RESOScript

  @REQ-WA103-END3 @core
  Scenario: REQ-WA103-QR3 - CORE - Request and Validate Server Metadata
    When a metadata request is made relative to the given service root in ClientSettings_WebAPIURI
    Then the server responds with a status code of 200
    And the metadata returned is valid

  @REQ-WA103-QR3 @gold
  Scenario: REQ-WA103-QR3 - CORE - Query Support: $select
    Given a case-sensitive OData $select list consisting of Parameter_SelectList in the given request
    When the request is issued against the Web API Server's Parameter_EndpointResource
    Then the server responds with a status code of 200
    And the results contain at most Parameter_TopCount records
    And data are present in fields contained within Parameter_SelectList

  @REQ_WA103_END2 @core
  Scenario: REQ-WA103-END2 - CORE - Data Systems Endpoint test
    Given the url for the server's DataSystem endpoint
    When the request is issued against the DataSystem endpoint
    Then the server responds with a status code of 200
    And the results are valid JSON
    And the results match the expected DataSystem schema
