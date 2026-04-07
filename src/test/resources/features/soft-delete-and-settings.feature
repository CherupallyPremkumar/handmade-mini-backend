Feature: Soft Delete & Admin Settings
  Products are soft-deleted, settings are admin-configurable

  Background:
    Given the following products exist:
      | name           | fabric | weaveType | color | sellingPrice | mrp    | stock | gstPct |
      | Delete Saree   | SILK   | IKAT      | Red   | 100000       | 150000 | 10    | 5      |

  Scenario: Soft-deleted product not in public list
    Given I am logged in as admin
    When I DELETE the first product with auth
    Then the response status is 204
    When I GET "/api/products"
    Then the response is an empty list

  Scenario: Soft-deleted product not in admin list
    Given I am logged in as admin
    When I DELETE the first product with auth
    Then the response status is 204
    When I GET "/api/admin/products" with auth token
    Then the response is an empty list

  Scenario: Admin can list settings
    Given I am logged in as admin
    When I GET "/api/admin/settings" with auth token
    Then the response status is 200

  Scenario: Admin can update a setting
    Given I am logged in as admin
    When I PUT "/api/admin/settings/shipping_cost" with auth and body:
      """
      {"value":"5000"}
      """
    Then the response status is 200

  Scenario: Non-admin cannot access settings
    Given I am logged in as "customer@test.com" with password "Secret@123"
    When I GET "/api/admin/settings" with auth token
    Then the response status is 403
