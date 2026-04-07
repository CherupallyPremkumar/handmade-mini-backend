Feature: Shopping Cart
  Session-based cart with stock validation

  Background:
    Given the following products exist:
      | name         | fabric | weaveType | color | sellingPrice | mrp     | stock | gstPct |
      | Silk Saree   | SILK   | IKAT      | Blue  | 850000       | 1200000 | 5     | 5      |
      | Cotton Saree | COTTON | IKAT      | Red   | 450000       | 600000  | 2     | 5      |

  Scenario: Empty cart returns empty list
    When I GET "/api/cart/sess-001"
    Then the response status is 200
    And the response is an empty list

  Scenario: Add item to cart
    When I add product "Silk Saree" to cart "sess-add" with quantity 1
    Then the response status is 201
    And the response JSON key "quantity" is 1
    And the response JSON key "sessionId" is "sess-add"

  Scenario: Adding same product merges quantity
    When I add product "Silk Saree" to cart "sess-merge" with quantity 1
    And I add product "Silk Saree" to cart "sess-merge" with quantity 2
    Then the response JSON key "quantity" is 3

  Scenario: Multiple products in cart
    When I add product "Silk Saree" to cart "sess-multi" with quantity 1
    And I add product "Cotton Saree" to cart "sess-multi" with quantity 1
    And I GET "/api/cart/sess-multi"
    Then the response is a list of 2 items

  Scenario: Remove item from cart
    When I add product "Silk Saree" to cart "sess-rm" with quantity 1
    And I store the cart item ID
    And I remove the stored item from cart "sess-rm"
    Then the response status is 204
    And cart "sess-rm" is empty

  Scenario: Clear entire cart
    When I add product "Silk Saree" to cart "sess-clr" with quantity 1
    And I add product "Cotton Saree" to cart "sess-clr" with quantity 1
    And I DELETE "/api/cart/sess-clr"
    Then the response status is 204
    And cart "sess-clr" is empty

  Scenario: Reject quantity exceeding stock
    When I add product "Cotton Saree" to cart "sess-over" with quantity 3
    Then the response status is 409
    And the response error contains "Insufficient stock"

  Scenario: Reject cumulative quantity exceeding stock
    When I add product "Cotton Saree" to cart "sess-cum" with quantity 2
    Then the response status is 201
    When I add product "Cotton Saree" to cart "sess-cum" with quantity 1
    Then the response status is 409

  Scenario: Non-existent product returns 404
    When I POST "/api/cart/sess-x/add" with body:
      """
      {"productId":"ghost","quantity":1}
      """
    Then the response status is 404

  Scenario: Cannot remove item from wrong session
    When I add product "Silk Saree" to cart "sess-own" with quantity 1
    And I store the cart item ID
    And I try to remove the stored item from cart "sess-other"
    Then the response status is 404

  Scenario: Add with zero quantity defaults to 1
    When I add product "Silk Saree" to cart "sess-zero" with quantity 0
    Then the response status is 201

  Scenario: Add inactive product fails
    Given the following products exist:
      | name             | fabric | weaveType | color | sellingPrice | mrp    | stock | gstPct |
      | Inactive Product | SILK   | IKAT      | Black | 100000       | 150000 | 5     | 5      |
    When I POST "/api/cart/sess-inactive/add" with body:
      """
      {"productId":"non-existent","quantity":1}
      """
    Then the response status is 404

  Scenario: Remove non-existent cart item returns 404
    When I DELETE "/api/cart/sess-x/ghost-item-id"
    Then the response status is 404

  Scenario: Get cart for non-existent session returns empty
    When I GET "/api/cart/does-not-exist-session"
    Then the response status is 200
    And the response is an empty list

  Scenario: Add same product to different sessions keeps separate
    When I add product "Silk Saree" to cart "sess-a" with quantity 1
    And I add product "Silk Saree" to cart "sess-b" with quantity 2
    And I GET "/api/cart/sess-a"
    Then the response is a list of 1 items
    When I GET "/api/cart/sess-b"
    Then the response is a list of 1 items
