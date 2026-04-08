Feature: Product Reviews & Ratings
  Only purchasers can review, admin moderates

  Background:
    Given the following products exist:
      | name           | fabric | weaveType | color | sellingPrice | mrp    | stock | gstPct |
      | Review Saree   | SILK   | IKAT      | Blue  | 100000       | 150000 | 10    | 5      |

  Scenario: Non-purchaser cannot submit review
    Given I am logged in as "reviewer1@test.com" with password "Secret@123"
    When I submit review for "Review Saree" with rating 5 and comment "I want to review"
    Then the response status is 409
    And the response error contains "purchased"

  Scenario: Review with invalid rating rejected
    Given I am logged in as "reviewer3@test.com" with password "Secret@123"
    When I submit review for "Review Saree" with rating 6 and comment "Invalid"
    Then the response status is 404

  Scenario: Review summary returns zero for no reviews
    When I GET review summary for "Review Saree"
    Then the response status is 200
    And the response JSON key "reviewCount" is 0

  Scenario: Public reviews list is empty when none approved
    When I GET reviews for "Review Saree"
    Then the response status is 200
    And the response is an empty list

  Scenario: Admin can view pending reviews
    Given I am logged in as admin
    When I GET "/api/admin/reviews/pending" with auth token
    Then the response status is 200

  Scenario: Unauthenticated user cannot submit review
    When I POST "/api/products/fake-id/reviews" with body:
      """
      {"rating":5,"comment":"nope"}
      """
    Then the response status is 403
