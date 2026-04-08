Feature: Wishlist
  Authenticated users can save products to their wishlist

  Background:
    Given the following products exist:
      | name            | fabric | weaveType | color | sellingPrice | mrp    | stock | gstPct |
      | Wishlist Silk   | SILK   | IKAT      | Blue  | 100000       | 150000 | 10    | 5      |
      | Wishlist Cotton | COTTON | HANDLOOM  | Red   | 50000        | 80000  | 5     | 5      |

  Scenario: Add product to wishlist
    Given I am logged in as "wish1@test.com" with password "Secret@123"
    When I add "Wishlist Silk" to wishlist
    Then the response status is 200

  Scenario: Wishlist returns saved products
    Given I am logged in as "wish2@test.com" with password "Secret@123"
    When I add "Wishlist Silk" to wishlist
    When I GET "/api/wishlist" with auth token
    Then the response status is 200
    And the response is a list of 1 items

  Scenario: Duplicate add returns OK (idempotent)
    Given I am logged in as "wish3@test.com" with password "Secret@123"
    When I add "Wishlist Silk" to wishlist
    When I add "Wishlist Silk" to wishlist
    When I GET "/api/wishlist" with auth token
    Then the response status is 200
    And the response is a list of 1 items

  Scenario: Remove from wishlist
    Given I am logged in as "wish4@test.com" with password "Secret@123"
    When I add "Wishlist Silk" to wishlist
    When I remove "Wishlist Silk" from wishlist
    When I GET "/api/wishlist" with auth token
    Then the response status is 200
    And the response is an empty list

  Scenario: Check if product is in wishlist
    Given I am logged in as "wish5@test.com" with password "Secret@123"
    When I add "Wishlist Silk" to wishlist
    When I check wishlist for "Wishlist Silk"
    Then the response status is 200
    And the response JSON key "inWishlist" is "true"

  Scenario: Check returns false for product not in wishlist
    Given I am logged in as "wish6@test.com" with password "Secret@123"
    When I check wishlist for "Wishlist Cotton"
    Then the response status is 200
    And the response JSON key "inWishlist" is "false"

  Scenario: Add non-existent product returns 404
    Given I am logged in as "wish7@test.com" with password "Secret@123"
    When I POST "/api/wishlist" with auth and body:
      """
      {"productId":"ghost-product-id"}
      """
    Then the response status is 404

  Scenario: Unauthenticated wishlist access returns 403
    When I GET "/api/wishlist"
    Then the response status is 403
