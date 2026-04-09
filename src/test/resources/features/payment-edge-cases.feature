Feature: Payment Edge Cases
  Shipping address validation, rate limiting, spam prevention, webhook validation, payment failure handling

  Background:
    Given the following products exist:
      | name          | fabric | weaveType | color | sellingPrice | mrp    | stock | gstPct |
      | Edge Saree    | SILK   | IKAT      | Blue  | 100000       | 150000 | 10    | 5      |
      | Scarce Saree  | SILK   | IKAT      | Red   | 100000       | 150000 | 1     | 5      |

  # ─── Shipping Address Validation ───

  Scenario: Missing shipping address line1 returns 400
    Given I am logged in as "addr1@test.com" with password "Secret@123"
    When I POST "/api/checkout/create-order" with auth and body:
      """
      {"customerName":"X","customerPhone":"+911111111111","customerEmail":"addr1@test.com","shippingAddress":{"city":"Hyderabad","state":"Telangana","pincode":"500001"},"items":[{"productId":"fake","quantity":1}]}
      """
    Then the response status is 400
    And the response error contains "line1, city, state, pincode"

  Scenario: Blank shipping pincode returns 400
    Given I am logged in as "addr2@test.com" with password "Secret@123"
    When I POST "/api/checkout/create-order" with auth and body:
      """
      {"customerName":"X","customerPhone":"+912222222222","customerEmail":"addr2@test.com","shippingAddress":{"line1":"123 St","city":"Hyderabad","state":"Telangana","pincode":""},"items":[{"productId":"fake","quantity":1}]}
      """
    Then the response status is 400
    And the response error contains "line1, city, state, pincode"

  Scenario: Empty shipping address object returns 400
    Given I am logged in as "addr3@test.com" with password "Secret@123"
    When I POST "/api/checkout/create-order" with auth and body:
      """
      {"customerName":"X","customerPhone":"+913333333333","customerEmail":"addr3@test.com","shippingAddress":{},"items":[{"productId":"fake","quantity":1}]}
      """
    Then the response status is 400
    And the response error contains "line1, city, state, pincode"

  # ─── Webhook Payload Validation ───

  Scenario: Webhook with missing order_id returns 200 (non-retryable)
    When I POST "/api/webhooks/razorpay" with webhook signature "valid" and body:
      """
      {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_x"}}}}
      """
    Then the response status is 200

  Scenario: Webhook with empty order_id returns 200 (non-retryable)
    When I POST "/api/webhooks/razorpay" with webhook signature "valid" and body:
      """
      {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_x","order_id":""}}}}
      """
    Then the response status is 200

  Scenario: Webhook with missing event field is rejected
    When I POST "/api/webhooks/razorpay" with webhook signature "valid" and body:
      """
      {"payload":{"payment":{"entity":{"id":"pay_x","order_id":"ord_x"}}}}
      """
    Then the response status is 400

  # ─── markPaymentFailed idempotency ───

  Scenario: payment.failed webhook does not overwrite PAID order
    Given I am logged in as "paid-safe@test.com" with password "Secret@123"
    And product "Edge Saree" has stock 10
    And I have placed an order for "Edge Saree" quantity 1
    When I send a Razorpay webhook "payment.captured" with valid signature
    Then the response status is 200
    And the order status is now "PAID"
    When I send a Razorpay webhook "payment.failed" with valid signature
    Then the response status is 200
    And the order status is now "PAID"

  # ─── Rate Limiting on verify-payment ───

  Scenario: Verify payment rate limited after 5 attempts
    Given I am logged in as "rate1@test.com" with password "Secret@123"
    When I POST "/api/checkout/verify-payment" with auth and body:
      """
      {"razorpayOrderId":"order_fake","razorpayPaymentId":"pay_fake","razorpaySignature":"bad"}
      """
    Then the response status is 400
    When I POST "/api/checkout/verify-payment" with auth and body:
      """
      {"razorpayOrderId":"order_fake","razorpayPaymentId":"pay_fake","razorpaySignature":"bad"}
      """
    Then the response status is 400
    When I POST "/api/checkout/verify-payment" with auth and body:
      """
      {"razorpayOrderId":"order_fake","razorpayPaymentId":"pay_fake","razorpaySignature":"bad"}
      """
    Then the response status is 400
    When I POST "/api/checkout/verify-payment" with auth and body:
      """
      {"razorpayOrderId":"order_fake","razorpayPaymentId":"pay_fake","razorpaySignature":"bad"}
      """
    Then the response status is 400
    When I POST "/api/checkout/verify-payment" with auth and body:
      """
      {"razorpayOrderId":"order_fake","razorpayPaymentId":"pay_fake","razorpaySignature":"bad"}
      """
    Then the response status is 400
    When I POST "/api/checkout/verify-payment" with auth and body:
      """
      {"razorpayOrderId":"order_fake","razorpayPaymentId":"pay_fake","razorpaySignature":"bad"}
      """
    Then the response status is 429
    And the response error contains "Too many verification attempts"

  # ─── GET Payment Callback ───

  Scenario: GET payment-callback redirects to frontend error page
    When I GET "/api/checkout/payment-callback"
    Then the response status is 302

  Scenario: GET payment-callback redirects without cancelling order (unsigned)
    Given I am logged in as "getfail@test.com" with password "Secret@123"
    And I have placed an order for "Edge Saree" quantity 1
    When I GET payment-callback with the order's Razorpay ID
    Then the response status is 302
    And the order status is now "PENDING_PAYMENT"

  # ─── Pending Order Spam Prevention ───

  Scenario: Max 3 pending orders per phone number
    Given I am logged in as "spam1@test.com" with password "Secret@123"
    When I create order with phone "+914444444444" and email "a@test.com" for "Edge Saree" quantity 1
    Then the response status is 200
    When I create order with phone "+914444444444" and email "b@test.com" for "Edge Saree" quantity 2
    Then the response status is 200
    When I create order with phone "+914444444444" and email "c@test.com" for "Edge Saree" quantity 3
    Then the response status is 200
    When I create order with phone "+914444444444" and email "d@test.com" for "Edge Saree" quantity 1
    Then the response status is 409
    And the response error contains "Too many pending orders"
