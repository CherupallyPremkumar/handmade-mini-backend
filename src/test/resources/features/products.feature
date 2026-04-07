Feature: Product Catalog
  Public browsing + admin CRUD for products

  Background:
    Given the following products exist:
      | name              | fabric      | weaveType   | color  | sellingPrice | mrp     | stock | gstPct |
      | Royal Blue Silk   | SILK        | IKAT        | Blue   | 850000       | 1200000 | 5     | 5      |
      | Red Cotton Ikat   | COTTON      | IKAT        | Red    | 450000       | 600000  | 10    | 5      |
      | Green Silk Cotton | SILK_COTTON | TELIA_RUMAL | Green  | 650000       | 800000  | 3     | 12     |
      | Inactive Product  | SILK        | HANDLOOM    | Black  | 300000       | 400000  | 0     | 5      |

  Scenario: List active products only
    When I GET "/api/products"
    Then the response status is 200
    And the response is a list of 3 products
    And product "Inactive Product" is not in the list

  Scenario: Get product by ID
    When I GET the first product by ID
    Then the response status is 200
    And the response JSON key "name" is "Royal Blue Silk"

  Scenario: Non-existent product returns 404
    When I GET "/api/products/does-not-exist"
    Then the response status is 404
    And the response error contains "Product not found"

  Scenario: Filter by fabric
    When I GET "/api/products?fabric=SILK"
    Then the response status is 200
    And the response is a list of 1 products

  Scenario: Filter by weave type
    When I GET "/api/products?weaveType=IKAT"
    Then the response status is 200
    And the response is a list of 2 products

  Scenario: Filter by color
    When I GET "/api/products?color=Green"
    Then the response status is 200
    And the response is a list of 1 products

  Scenario: Filter by price range
    When I GET "/api/products?minPrice=400000&maxPrice=700000"
    Then the response status is 200
    And the response is a list of 2 products

  Scenario: Search by name
    When I GET "/api/products?search=Royal"
    Then the response status is 200
    And the response list contains product "Royal Blue Silk"

  Scenario: Admin creates product
    Given I am logged in as admin
    When I POST "/api/admin/products" with auth and body:
      """
      {"name":"New Saree","fabric":"SILK","weaveType":"IKAT","color":"Purple","sellingPrice":500000,"mrp":700000,"stock":8,"gstPct":5,"hsnCode":"50079090"}
      """
    Then the response status is 201
    And the response JSON key "name" is "New Saree"
    And the response JSON key "id" is not empty

  Scenario: Admin updates product
    Given I am logged in as admin
    When I update the first product name to "Updated Silk"
    Then the response status is 200
    And the response JSON key "name" is "Updated Silk"

  Scenario: Admin soft-deletes product
    Given I am logged in as admin
    When I DELETE the first product with auth
    Then the response status is 204
    And the deleted product is no longer in active list

  Scenario: Customer cannot create products
    Given I am logged in as customer
    When I POST "/api/admin/products" with auth and body:
      """
      {"name":"Hack","fabric":"SILK","weaveType":"IKAT","sellingPrice":100,"mrp":200,"stock":1}
      """
    Then the response status is 403

  Scenario: Anonymous cannot create products
    When I POST "/api/admin/products" with body:
      """
      {"name":"Hack","fabric":"SILK","weaveType":"IKAT","sellingPrice":100,"mrp":200,"stock":1}
      """
    Then the response status is 403

  Scenario: Create product with missing name fails
    Given I am logged in as admin
    When I POST "/api/admin/products" with auth and body:
      """
      {"fabric":"SILK","weaveType":"IKAT","sellingPrice":100,"mrp":200,"stock":1}
      """
    Then the response status is 400

  Scenario: Create product with negative price fails
    Given I am logged in as admin
    When I POST "/api/admin/products" with auth and body:
      """
      {"name":"Bad","fabric":"SILK","weaveType":"IKAT","sellingPrice":-100,"mrp":200,"stock":1}
      """
    Then the response status is 400

  Scenario: Delete non-existent product returns 404
    Given I am logged in as admin
    When I DELETE "/api/admin/products/ghost-id" with auth
    Then the response status is 404

  Scenario: Update non-existent product returns 404
    Given I am logged in as admin
    When I PUT "/api/admin/products/ghost-id" with auth and body:
      """
      {"name":"X","fabric":"SILK","weaveType":"IKAT","sellingPrice":100,"mrp":200,"stock":1}
      """
    Then the response status is 404

  Scenario: Invalid fabric enum in filter is rejected
    When I GET "/api/products?fabric=DIAMOND"
    Then the response status is 400

  Scenario: Create product with zero stock is allowed
    Given I am logged in as admin
    When I POST "/api/admin/products" with auth and body:
      """
      {"name":"Zero Stock","fabric":"SILK","weaveType":"IKAT","color":"Red","sellingPrice":100000,"mrp":200000,"stock":0,"gstPct":5,"hsnCode":"50079090"}
      """
    Then the response status is 201

  Scenario: Create product with empty name fails
    Given I am logged in as admin
    When I POST "/api/admin/products" with auth and body:
      """
      {"name":"   ","fabric":"SILK","weaveType":"IKAT","color":"Red","sellingPrice":100000,"mrp":200000,"stock":5,"gstPct":5,"hsnCode":"50079090"}
      """
    Then the response status is 400

  Scenario: Update product preserves other fields
    Given I am logged in as admin
    When I GET the first product by ID
    Then the response JSON key "fabric" is "SILK"
