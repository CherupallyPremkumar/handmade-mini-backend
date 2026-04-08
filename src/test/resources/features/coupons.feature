Feature: Coupons & Discount Codes
  Coupon validation, edge cases, admin CRUD

  Background:
    Given the following products exist:
      | name          | fabric | weaveType | color | sellingPrice | mrp    | stock | gstPct |
      | Coupon Saree  | SILK   | IKAT      | Blue  | 100000       | 150000 | 10    | 5      |

  Scenario: Admin creates percentage coupon
    Given I am logged in as admin
    When I POST "/api/admin/coupons" with auth and body:
      """
      {"code":"WELCOME20","type":"PERCENTAGE","value":20}
      """
    Then the response status is 200
    And the response JSON key "code" is "WELCOME20"

  Scenario: Admin creates fixed amount coupon
    Given I am logged in as admin
    When I POST "/api/admin/coupons" with auth and body:
      """
      {"code":"FLAT500","type":"FIXED","value":50000}
      """
    Then the response status is 200

  Scenario: Validate valid percentage coupon
    Given I am logged in as admin
    When I POST "/api/admin/coupons" with auth and body:
      """
      {"code":"TEST10","type":"PERCENTAGE","value":10}
      """
    Then the response status is 200
    Given I am logged in as "coupon1@test.com" with password "Secret@123"
    When I POST "/api/coupons/validate" with auth and body:
      """
      {"code":"TEST10","orderAmount":100000}
      """
    Then the response status is 200
    And the response JSON key "valid" is "true"

  Scenario: Invalid coupon code rejected
    Given I am logged in as "coupon2@test.com" with password "Secret@123"
    When I POST "/api/coupons/validate" with auth and body:
      """
      {"code":"NOTREAL","orderAmount":100000}
      """
    Then the response status is 200
    And the response JSON key "valid" is "false"

  Scenario: Expired coupon rejected
    Given I am logged in as admin
    When I POST "/api/admin/coupons" with auth and body:
      """
      {"code":"EXPIRED","type":"PERCENTAGE","value":10,"validTo":"2020-01-01T00:00:00Z"}
      """
    Then the response status is 200
    Given I am logged in as "coupon3@test.com" with password "Secret@123"
    When I POST "/api/coupons/validate" with auth and body:
      """
      {"code":"EXPIRED","orderAmount":100000}
      """
    Then the response status is 200
    And the response JSON key "valid" is "false"

  Scenario: Coupon below minimum order rejected
    Given I am logged in as admin
    When I POST "/api/admin/coupons" with auth and body:
      """
      {"code":"MIN999","type":"PERCENTAGE","value":10,"minOrderAmount":99900}
      """
    Then the response status is 200
    Given I am logged in as "coupon4@test.com" with password "Secret@123"
    When I POST "/api/coupons/validate" with auth and body:
      """
      {"code":"MIN999","orderAmount":50000}
      """
    Then the response status is 200
    And the response JSON key "valid" is "false"

  Scenario: Admin lists all coupons
    Given I am logged in as admin
    When I POST "/api/admin/coupons" with auth and body:
      """
      {"code":"LIST1","type":"FIXED","value":10000}
      """
    Then the response status is 200
    When I GET "/api/admin/coupons" with auth token
    Then the response status is 200

  Scenario: Non-admin cannot create coupons
    Given I am logged in as "nonadmin@test.com" with password "Secret@123"
    When I POST "/api/admin/coupons" with auth and body:
      """
      {"code":"NOPE","type":"PERCENTAGE","value":10}
      """
    Then the response status is 403

  Scenario: Admin deletes coupon
    Given I am logged in as admin
    When I POST "/api/admin/coupons" with auth and body:
      """
      {"code":"DELETE_ME","type":"FIXED","value":5000}
      """
    Then the response status is 200
