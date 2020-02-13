Feature: Web API Server 1.0.2 Certification
  All Scenarios Passing means that the Web API server is fully-compliant with the RESO specification.
  It's not expected that a server will pass all scenarios.

  Background:
    Given a RESOScript file was provided
    And Client Settings and Parameters were read from the file
    And an OData client was successfully created from the given RESOScript

  @REQ-WA103-END3 @core
  Scenario: REQ-WA103-QR3 - CORE - Request and Validate Server Metadata
    When a GET request is made to the resolved Url in "REQ-WA103-END3"
    Then the server responds with a status code of 200
    And the response is valid XML
    And the metadata returned is valid

  @REQ_WA103_END2 @core
  Scenario: REQ-WA103-END2 - CORE - Data Systems Endpoint test
    When a GET request is made to the resolved Url in "REQ-WA103-END2"
    Then the server responds with a status code of 200
    And the response is valid JSON
    And the results match the expected DataSystem JSON schema

  @REQ_WA103_QR1 @core
  Scenario: REQ-WA103-QR1 - CORE - Search Parameters: Search by UniqueID
    When a GET request is made to the resolved Url in "REQ-WA103-QR1"
    Then the server responds with a status code of 200
    And the response is valid JSON
    And the provided "Parameter_UniqueIDValue" is returned in the "Parameter_UniqueID" field

  @REQ-WA103-QR3 @gold
  Scenario: REQ-WA103-QR3 - CORE - Query Support: $select
    When a GET request is made to the resolved Url in "REQ-WA103-QR3"
    Then the server responds with a status code of 200
    And the response is valid JSON
    And the results contain at most "Parameter_TopCount" records
    And data are present in fields contained within "Parameter_SelectList"
    And data in "Parameter_FilterNumericField" are greater than "Parameter_FilterNumericValueLow"
