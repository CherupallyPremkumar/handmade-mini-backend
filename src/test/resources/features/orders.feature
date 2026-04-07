Feature: Order Management
  Public order tracking + admin status workflow

  Background:
    Given the following products exist:
      | name        | fabric | weaveType | color | sellingPrice | mrp    | stock | gstPct |
      | Order Saree | SILK   | IKAT      | Blue  | 200000       | 300000 | 50    | 5      |

  Scenario: Track order by number (PII hidden)
    Given I am logged in as "track@test.com" with password "Secret@123"
    And I have placed an order for "Order Saree" quantity 1
    When I GET the order by order number without auth
    Then the response status is 200
    And the tracking response has order number
    And the tracking response has status "PENDING_PAYMENT"
    And the tracking response hides customer PII

  Scenario: Track non-existent order returns 404
    When I GET "/api/orders/FAKE-ORDER"
    Then the response status is 404

  Scenario: PLACED to PAID
    Given I am logged in as "pay@test.com" with password "Secret@123"
    And I have placed an order for "Order Saree" quantity 1
    And I am logged in as admin
    When I update order status to "PAID"
    Then the response status is 200
    And the response JSON key "status" is "PAID"

  Scenario: PAID to SHIPPED with tracking number
    Given I am logged in as "ship@test.com" with password "Secret@123"
    And I have placed an order for "Order Saree" quantity 1
    And I am logged in as admin
    And I update order status to "PAID"
    When I update order status to "SHIPPED" with tracking "TRACK123"
    Then the response status is 200
    And the response JSON key "status" is "SHIPPED"
    And the response JSON key "trackingNumber" is "TRACK123"

  Scenario: SHIPPED to DELIVERED
    Given I am logged in as "dlvr@test.com" with password "Secret@123"
    And I have placed an order for "Order Saree" quantity 1
    And I am logged in as admin
    And I update order status to "PAID"
    And I update order status to "SHIPPED" with tracking "T1"
    When I update order status to "DELIVERED"
    Then the response status is 200
    And the response JSON key "status" is "DELIVERED"

  Scenario: Cannot skip PENDING_PAYMENT to SHIPPED
    Given I am logged in as "skip@test.com" with password "Secret@123"
    And I have placed an order for "Order Saree" quantity 1
    And I am logged in as admin
    When I update order status to "SHIPPED"
    Then the response status is 409
    And the response error contains "Invalid status transition"

  Scenario: Cannot change DELIVERED order
    Given I am logged in as "final@test.com" with password "Secret@123"
    And I have placed an order for "Order Saree" quantity 1
    And I am logged in as admin
    And I update order status to "PAID"
    And I update order status to "SHIPPED" with tracking "T2"
    And I update order status to "DELIVERED"
    When I update order status to "CANCELLED"
    Then the response status is 409

  Scenario: Cancel PLACED order
    Given I am logged in as "cancel@test.com" with password "Secret@123"
    And I have placed an order for "Order Saree" quantity 1
    And I am logged in as admin
    When I update order status to "CANCELLED"
    Then the response status is 200
    And the response JSON key "status" is "CANCELLED"

  Scenario: Admin lists all orders
    Given I am logged in as admin
    When I GET "/api/admin/orders" with auth token
    Then the response status is 200

  Scenario: Admin filters orders by status
    Given I am logged in as admin
    When I GET "/api/admin/orders?status=PLACED" with auth token
    Then the response status is 200

  Scenario: Customer cannot list orders
    Given I am logged in as customer
    When I GET "/api/admin/orders" with auth token
    Then the response status is 403

  Scenario: Update non-existent order returns 404
    Given I am logged in as admin
    When I PATCH "/api/admin/orders/ghost-id/status" with auth and body:
      """
      {"status":"PAID"}
      """
    Then the response status is 404

  Scenario: Update order without status field returns 400
    Given I am logged in as "nostatus@test.com" with password "Secret@123"
    And I have placed an order for "Order Saree" quantity 1
    And I am logged in as admin
    When I PATCH "/api/admin/orders/{savedOrderId}/status" with auth and body:
      """
      {"trackingNumber":"T1"}
      """
    Then the response status is 400

  Scenario: Customer cannot update order status
    Given I am logged in as "custupd@test.com" with password "Secret@123"
    And I have placed an order for "Order Saree" quantity 1
    When I PATCH "/api/admin/orders/{savedOrderId}/status" with auth and body:
      """
      {"status":"PAID"}
      """
    Then the response status is 403

  Scenario: Invalid status enum returns 400
    Given I am logged in as "badenum@test.com" with password "Secret@123"
    And I have placed an order for "Order Saree" quantity 1
    And I am logged in as admin
    When I PATCH "/api/admin/orders/{savedOrderId}/status" with auth and body:
      """
      {"status":"EXPLODED"}
      """
    Then the response status is 400

  Scenario: Cannot cancel DELIVERED order
    Given I am logged in as "nocancel@test.com" with password "Secret@123"
    And I have placed an order for "Order Saree" quantity 1
    And I am logged in as admin
    And I update order status to "PAID"
    And I update order status to "SHIPPED" with tracking "T99"
    And I update order status to "DELIVERED"
    When I update order status to "CANCELLED"
    Then the response status is 409

  Scenario: Double payment is idempotent
    Given I am logged in as "idempotent@test.com" with password "Secret@123"
    And I have placed an order for "Order Saree" quantity 1
    And I am logged in as admin
    And I update order status to "PAID"
    When I update order status to "PAID"
    Then the response status is 409
