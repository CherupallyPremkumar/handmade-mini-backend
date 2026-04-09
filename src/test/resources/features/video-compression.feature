Feature: Video Compression Webhook
  Lambda callback webhook for async video compression status updates.
  Authenticated via HMAC-SHA256 shared secret.

  Background:
    Given the following products exist:
      | name         | fabric | weaveType | color | sellingPrice | mrp    | stock | gstPct |
      | Video Saree  | SILK   | IKAT      | Red   | 200000       | 300000 | 5     | 5      |
    And a video webhook secret is configured

  Scenario: Missing signature header is rejected
    When I POST "/api/admin/videos/compression-done" with video webhook body:
      """
      {"productId":"PLACEHOLDER","status":"READY","videoUrl":"https://cdn.example.com/compressed.mp4"}
      """
    And without webhook signature
    Then the response status is 401
    And the response error contains "Missing signature"

  Scenario: Invalid signature is rejected
    When I POST "/api/admin/videos/compression-done" with video webhook body:
      """
      {"productId":"PLACEHOLDER","status":"READY","videoUrl":"https://cdn.example.com/compressed.mp4"}
      """
    And with invalid webhook signature
    Then the response status is 401
    And the response error contains "Invalid signature"

  Scenario: Valid READY webhook updates product to compressed URL
    Given product "Video Saree" starts with videoStatus "COMPRESSING"
    When I POST "/api/admin/videos/compression-done" with video webhook body:
      """
      {"productId":"PLACEHOLDER","status":"READY","videoUrl":"https://cdn.example.com/videos/compressed.mp4","originalSizeBytes":500000000,"compressedSizeBytes":25000000}
      """
    And with valid webhook signature
    Then the response status is 200
    And product "Video Saree" has videoStatus "READY"
    And product "Video Saree" videoUrl is "https://cdn.example.com/videos/compressed.mp4"

  Scenario: Valid FAILED webhook marks product as FAILED
    Given product "Video Saree" starts with videoStatus "COMPRESSING"
    When I POST "/api/admin/videos/compression-done" with video webhook body:
      """
      {"productId":"PLACEHOLDER","status":"FAILED","error":"ffmpeg exited 1"}
      """
    And with valid webhook signature
    Then the response status is 200
    And product "Video Saree" has videoStatus "FAILED"

  Scenario: Duplicate READY webhook is idempotent
    Given product "Video Saree" starts with videoStatus "READY"
    When I POST "/api/admin/videos/compression-done" with video webhook body:
      """
      {"productId":"PLACEHOLDER","status":"READY","videoUrl":"https://cdn.example.com/second.mp4"}
      """
    And with valid webhook signature
    Then the response status is 200
    And the response JSON key "duplicate" is true

  Scenario: Webhook returns 503 when secret not configured
    Given the video webhook secret is NOT configured
    When I POST "/api/admin/videos/compression-done" with video webhook body:
      """
      {"productId":"PLACEHOLDER","status":"READY","videoUrl":"https://cdn.example.com/compressed.mp4"}
      """
    And with valid webhook signature
    Then the response status is 503
