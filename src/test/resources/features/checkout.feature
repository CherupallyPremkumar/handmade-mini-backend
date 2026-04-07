Feature: Checkout & Payment
  Authenticated users create orders, Razorpay handles payment

  Background:
    Given the following products exist:
      | name           | fabric | weaveType | color | sellingPrice | mrp    | stock | gstPct |
      | Checkout Silk  | SILK   | IKAT      | Blue  | 100000       | 150000 | 10    | 5      |
      | Expensive Silk | SILK   | IKAT      | Gold  | 120000       | 180000 | 5     | 5      |

  Scenario: Create order returns Razorpay details
    Given I am logged in as "b1@test.com" with password "Secret@123"
    When I create an order with auth for:
      | productName   | quantity |
      | Checkout Silk | 2        |
    Then the response status is 200
    And the response JSON key "orderNumber" starts with "DHN-"
    And the response JSON key "razorpayOrderId" starts with "order_test_"
    And the response JSON key "currency" is "INR"
    And the response JSON key "customerName" is not empty

  Scenario: GST calculated at 5%
    Given I am logged in as "b2@test.com" with password "Secret@123"
    When I create an order with auth for:
      | productName   | quantity |
      | Checkout Silk | 1        |
    Then the response status is 200
    And the order amount includes 5 percent GST on 100000 paisa

  Scenario: Free shipping above Rs 999
    Given I am logged in as "b3@test.com" with password "Secret@123"
    When I create an order with auth for:
      | productName   | quantity |
      | Checkout Silk | 10       |
    Then the response status is 200
    And the order has free shipping

  Scenario: Shipping charged below Rs 999
    Given the following products exist:
      | name         | fabric | weaveType | color | sellingPrice | mrp   | stock | gstPct |
      | Cheap Cotton | COTTON | IKAT      | Red   | 50000        | 80000 | 10    | 5      |
    Given I am logged in as "b4@test.com" with password "Secret@123"
    When I create an order with auth for:
      | productName  | quantity |
      | Cheap Cotton | 1        |
    Then the response status is 200
    And the order has shipping cost of 9900 paisa

  Scenario: Stock NOT decremented on order creation (only on payment)
    Given I am logged in as "b5@test.com" with password "Secret@123"
    And product "Checkout Silk" has stock 10
    When I create an order with auth for:
      | productName   | quantity |
      | Checkout Silk | 3        |
    Then the response status is 200
    And product "Checkout Silk" now has stock 10

  Scenario: Order exceeding stock is rejected
    Given I am logged in as "b6@test.com" with password "Secret@123"
    When I create an order with auth for:
      | productName    | quantity |
      | Expensive Silk | 99       |
    Then the response status is 409
    And the response error contains "Insufficient stock"

  Scenario: Per-product GST calculation (mixed 5% and 12%)
    Given the following products exist:
      | name        | fabric      | weaveType   | color  | sellingPrice | mrp    | stock | gstPct |
      | Silk 5pct   | SILK        | IKAT        | Blue   | 100000       | 150000 | 10    | 5      |
      | Cotton 12pct| SILK_COTTON | TELIA_RUMAL | Green  | 200000       | 250000 | 10    | 12     |
    Given I am logged in as "gst@test.com" with password "Secret@123"
    When I create an order with auth for:
      | productName  | quantity |
      | Silk 5pct    | 1        |
      | Cotton 12pct | 1        |
    Then the response status is 200
    And the order GST is 29000 paisa for subtotal 300000 paisa

  Scenario: Unauthenticated checkout returns 403
    When I POST "/api/checkout/create-order" with body:
      """
      {"customerName":"Anon","customerPhone":"+91999","shippingAddress":{"line1":"x","city":"y","state":"z","pincode":"111111"},"items":[{"productId":"x","quantity":1}]}
      """
    Then the response status is 403

  Scenario: Missing required fields returns 400
    Given I am logged in as "b7@test.com" with password "Secret@123"
    When I POST "/api/checkout/create-order" with auth and body:
      """
      {"items":[{"productId":"x","quantity":1}]}
      """
    Then the response status is 400
    And the response error contains "Missing required fields"

  Scenario: Empty items returns 400
    Given I am logged in as "b8@test.com" with password "Secret@123"
    When I POST "/api/checkout/create-order" with auth and body:
      """
      {"customerName":"X","customerPhone":"+91999","shippingAddress":{"line1":"x","city":"y","state":"z","pincode":"111111"},"items":[]}
      """
    Then the response status is 400

  Scenario: Non-existent product in checkout items
    Given I am logged in as "b9@test.com" with password "Secret@123"
    When I POST "/api/checkout/create-order" with auth and body:
      """
      {"customerName":"X","customerPhone":"+91999","customerEmail":"x@x.com","shippingAddress":{"line1":"x","city":"y","state":"z","pincode":"111111"},"items":[{"productId":"ghost-product","quantity":1}]}
      """
    Then the response status is 404
    And the response error contains "Product not found"

  Scenario: Missing productId in checkout items
    Given I am logged in as "b11@test.com" with password "Secret@123"
    When I POST "/api/checkout/create-order" with auth and body:
      """
      {"customerName":"X","customerPhone":"+91999","customerEmail":"x@x.com","shippingAddress":{"line1":"x","city":"y","state":"z","pincode":"111111"},"items":[{"productId":null,"quantity":1}]}
      """
    Then the response status is 404

  Scenario: Zero quantity in checkout items
    Given I am logged in as "b10@test.com" with password "Secret@123"
    When I POST "/api/checkout/create-order" with auth and body:
      """
      {"customerName":"X","customerPhone":"+91999","customerEmail":"x@x.com","shippingAddress":{"line1":"x","city":"y","state":"z","pincode":"111111"},"items":[{"productId":"fake","quantity":0}]}
      """
    Then the response status is 404

  Scenario: Order created with PENDING_PAYMENT status
    Given I am logged in as "b12@test.com" with password "Secret@123"
    When I create an order with auth for:
      | productName   | quantity |
      | Checkout Silk | 1        |
    Then the response status is 200
    And the order status in DB is "PENDING_PAYMENT"

  Scenario: Multiple orders for same product allowed (stock not locked)
    Given I am logged in as "b13@test.com" with password "Secret@123"
    When I create an order with auth for:
      | productName   | quantity |
      | Checkout Silk | 5        |
    Then the response status is 200
    When I create an order with auth for:
      | productName   | quantity |
      | Checkout Silk | 5        |
    Then the response status is 200
    And product "Checkout Silk" now has stock 10
