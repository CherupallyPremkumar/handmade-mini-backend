Feature: Saved Addresses
  Authenticated users can save, update, delete shipping addresses

  Scenario: Create first address sets it as default
    Given I am logged in as "addr1@test.com" with password "Secret@123"
    When I POST "/api/addresses" with auth and body:
      """
      {"name":"Home","phone":"+919876543210","line1":"123 Main St","city":"Hyderabad","state":"Telangana","pincode":"500001","label":"Home"}
      """
    Then the response status is 200
    And the response JSON key "isDefault" is "true"
    And the response JSON key "name" is "Home"

  Scenario: List addresses returns saved addresses
    Given I am logged in as "addr2@test.com" with password "Secret@123"
    When I POST "/api/addresses" with auth and body:
      """
      {"name":"Office","phone":"+919876543211","line1":"456 Work Rd","city":"Chennai","state":"Tamil Nadu","pincode":"600001"}
      """
    Then the response status is 200
    When I GET "/api/addresses" with auth token
    Then the response status is 200
    And the response is a list of 1 items

  Scenario: Update address changes fields
    Given I am logged in as "addr3@test.com" with password "Secret@123"
    When I POST "/api/addresses" with auth and body:
      """
      {"name":"Old Name","phone":"+919876543212","line1":"789 St","city":"Delhi","state":"Delhi","pincode":"110001"}
      """
    Then the response status is 200

  Scenario: Delete address removes it
    Given I am logged in as "addr4@test.com" with password "Secret@123"
    When I POST "/api/addresses" with auth and body:
      """
      {"name":"Temp","phone":"+919876543213","line1":"000 St","city":"Mumbai","state":"Maharashtra","pincode":"400001"}
      """
    Then the response status is 200

  Scenario: Missing required address fields returns error
    Given I am logged in as "addr5@test.com" with password "Secret@123"
    When I POST "/api/addresses" with auth and body:
      """
      {"name":"No City","phone":"+919876543214","line1":"123 St"}
      """
    Then the response status is 404

  Scenario: Unauthenticated address access returns 403
    When I POST "/api/addresses" with body:
      """
      {"name":"Anon","phone":"+91999","line1":"x","city":"y","state":"z","pincode":"111111"}
      """
    Then the response status is 403
