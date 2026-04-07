Feature: Email Verification
  Users must verify email before checkout

  Background:
    Given the following products exist:
      | name          | fabric | weaveType | color | sellingPrice | mrp    | stock | gstPct |
      | Verify Saree  | SILK   | IKAT      | Blue  | 100000       | 150000 | 10    | 5      |

  Scenario: Registration returns emailVerified false
    When I register with name "New User" email "newreg@test.com" and password "Secret@123"
    Then the response status is 200
    And the response JSON key "emailVerified" is "false"

  Scenario: Login returns emailVerified status
    Given I am logged in as "verified@test.com" with password "Secret@123"
    When I login with email "verified@test.com" and password "Secret@123"
    Then the response status is 200
    And the response JSON key "emailVerified" is "true"

  Scenario: Unverified user cannot checkout
    When I register and login unverified as "unverified-chk@test.com" with password "Secret@123"
    When I create unverified order for "Verify Saree" quantity 1
    Then the response status is 403
    And the response error contains "verify your email"

  Scenario: Verify email with valid token succeeds
    When I register with name "TokenUser" email "token@test.com" and password "Secret@123"
    When I verify email for "token@test.com"
    Then the response status is 200
    And the response JSON key "verified" is "true"

  Scenario: Verify email with invalid token fails
    When I GET "/api/auth/verify-email?token=invalid_token_123"
    Then the response status is 404

  Scenario: Resend verification for non-existent email fails
    When I POST "/api/auth/resend-verification" with body:
      """
      {"email":"ghost@test.com"}
      """
    Then the response status is 404
