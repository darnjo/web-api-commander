Feature: Web API Server 1.0.2 Certification
  All Scenarios Passing means that the Web API server has is fully-compliant with the RESO specification.
  It's not expected that a server will pass all scenarios.

  Background:
    Given a RESOScript file was provided
    And Client Settings and Parameters were read from the file


  Scenario: CORE - Request and Validate Server Metadata
    Given an OData client was successfully created from the given RESOScript
    When a metadata request is made relative to the given Web API service root
    Then the server responds with a status code of "200"
    And the metadata returned is valid