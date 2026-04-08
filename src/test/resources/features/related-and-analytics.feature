Feature: Related Products, Analytics & Invoice
  Related product recommendations, admin analytics, invoice PDF

  Background:
    Given the following products exist:
      | name          | fabric | weaveType | color | sellingPrice | mrp    | stock | gstPct |
      | Silk One      | SILK   | IKAT      | Blue  | 100000       | 150000 | 10    | 5      |
      | Silk Two      | SILK   | IKAT      | Red   | 120000       | 180000 | 8     | 5      |
      | Cotton One    | COTTON | HANDLOOM  | Green | 50000        | 80000  | 15    | 5      |

  # ─── Related Products ───

  Scenario: Related products returns similar items
    When I GET related products for "Silk One"
    Then the response status is 200
    And the response list contains product "Silk Two"

  Scenario: Related products excludes itself
    When I GET related products for "Silk One"
    Then the response status is 200
    And product "Silk One" is not in the list

  Scenario: Related products for non-existent product returns 404
    When I GET "/api/products/ghost-id/related"
    Then the response status is 404

  # ─── Admin Analytics ───

  Scenario: Admin can access analytics
    Given I am logged in as admin
    When I GET "/api/admin/analytics" with auth token
    Then the response status is 200

  Scenario: Customer cannot access analytics
    Given I am logged in as "customer@test.com" with password "Secret@123"
    When I GET "/api/admin/analytics" with auth token
    Then the response status is 403

  # ─── Invoice ───

  Scenario: Invoice not available for pending orders
    Given I am logged in as "inv1@test.com" with password "Secret@123"
    And I have placed an order for "Silk One" quantity 1
    When I GET order invoice with auth
    Then the response status is 409

  Scenario: Admin can download invoice after payment
    Given I am logged in as "inv2@test.com" with password "Secret@123"
    And product "Silk One" has stock 10
    And I have placed an order for "Silk One" quantity 1
    When I send a Razorpay webhook "payment.captured" with valid signature
    Then the response status is 200
    Given I am logged in as admin
    When I GET order invoice with auth
    Then the response status is 200

  # ─── Public Settings ───

  Scenario: Public settings returns store info
    When I GET "/api/settings/public"
    Then the response status is 200
