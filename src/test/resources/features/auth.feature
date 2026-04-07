Feature: Authentication
  Users register with name/email/password and login to get a JWT token

  Scenario: Register a new customer
    When I register with name "Lakshmi Devi" email "lakshmi@test.com" and password "Secret@123"
    Then the response status is 200
    And the response contains a JWT token
    And the response has name "Lakshmi Devi"
    And the response has role "CUSTOMER"

  Scenario: Register without phone is allowed
    When I register with name "Priya" email "priya@test.com" and password "Secret@123"
    Then the response status is 200
    And the response contains a JWT token

  Scenario: Duplicate email is rejected
    Given a registered user with email "dup@test.com" and password "Secret@123"
    When I register with name "Another" email "dup@test.com" and password "Other@456"
    Then the response status is 409
    And the response error contains "already registered"

  Scenario: Blank name is rejected
    When I POST "/api/auth/register" with body:
      """
      {"name":"","email":"x@test.com","password":"Secret@123"}
      """
    Then the response status is 400

  Scenario: Invalid email is rejected
    When I POST "/api/auth/register" with body:
      """
      {"name":"X","email":"bad","password":"Secret@123"}
      """
    Then the response status is 400

  Scenario: Short password is rejected
    When I POST "/api/auth/register" with body:
      """
      {"name":"X","email":"s@test.com","password":"12345"}
      """
    Then the response status is 400

  Scenario: Login with valid credentials
    Given a registered user with email "login@test.com" and password "Secret@123"
    When I login with email "login@test.com" and password "Secret@123"
    Then the response status is 200
    And the response contains a JWT token

  Scenario: Login with wrong password
    Given a registered user with email "wrong@test.com" and password "Secret@123"
    When I login with email "wrong@test.com" and password "BadPassword"
    Then the response status is 401
    And the response error contains "Invalid email or password"

  Scenario: Login with unknown email
    When I login with email "ghost@test.com" and password "Secret@123"
    Then the response status is 401

  Scenario: Authenticated user accesses /auth/me
    Given I am logged in as "me@test.com" with password "Secret@123"
    When I GET "/api/auth/me" with auth token
    Then the response status is 200
    And the response JSON key "email" is "me@test.com"

  Scenario: Unauthenticated /auth/me returns 403
    When I GET "/api/auth/me" without auth
    Then the response status is 403

  Scenario: Empty request body on register
    When I POST "/api/auth/register" with body:
      """
      {}
      """
    Then the response status is 400

  Scenario: Empty request body on login
    When I POST "/api/auth/login" with body:
      """
      {}
      """
    Then the response status is 400

  Scenario: SQL injection in email field
    When I register with name "Hacker" email "' OR 1=1 --" and password "Secret@123"
    Then the response status is 400

  Scenario: Very long name is rejected
    When I POST "/api/auth/register" with body:
      """
      {"name":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","email":"long@test.com","password":"Secret@123"}
      """
    Then the response status is 400

  Scenario: Register with missing email field
    When I POST "/api/auth/register" with body:
      """
      {"name":"Test","password":"Secret@123"}
      """
    Then the response status is 400

  Scenario: Register with missing password field
    When I POST "/api/auth/register" with body:
      """
      {"name":"Test","email":"nopw@test.com"}
      """
    Then the response status is 400

  Scenario: Login with missing email
    When I POST "/api/auth/login" with body:
      """
      {"password":"Secret@123"}
      """
    Then the response status is 400

  Scenario: Login with missing password
    When I POST "/api/auth/login" with body:
      """
      {"email":"x@test.com"}
      """
    Then the response status is 400
