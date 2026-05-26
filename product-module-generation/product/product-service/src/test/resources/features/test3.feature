Feature: Testcase ID 3
Tests the product Workflow Service using a REST client. Product service exists and is under test.
It helps to create a product and manages the state of the product as documented in states xml

Scenario: Create a new product
Given that "flowName" equals "product-flow"
And that "initialState" equals "DRAFT"
When I POST a REST request to URL "/product" with payload
"""json
{
    "description": "Description",
    "name": "Gorgeous Pochampally Saree 3",
    "mrp": 500000,
    "sellingPrice": 450000,
    "stock": 10,
    "sku": "SKU-003",
    "hsnCode": "50079090",
    "gstPct": 5,
    "isActive": true,
    "isDeleted": false,
    "category": "SAREE",
    "blousePiece": false
}
"""
Then the REST response contains key "mutatedEntity"
And store "$.payload.mutatedEntity.id" from response to "id"
And the REST response key "mutatedEntity.currentState.stateId" is "${initialState}"
And store "$.payload.mutatedEntity.currentState.stateId" from response to "currentState"
And the REST response key "mutatedEntity.description" is "Description"

Scenario: Retrieve the product that just got created
When I GET a REST request to URL "/product/${id}"
Then the REST response contains key "mutatedEntity"
And the REST response key "mutatedEntity.id" is "${id}"
And the REST response key "mutatedEntity.currentState.stateId" is "${currentState}"


Scenario: Send the delete event to the product with comments
Given that "comment" equals "Comment for delete"
And that "event" equals "delete"
When I PATCH a REST request to URL "/product/${id}/${event}" with payload
"""json
{
    "comment": "${comment}"
}
"""
Then the REST response contains key "mutatedEntity"
And the REST response key "mutatedEntity.id" is "${id}"
And the REST response key "mutatedEntity.currentState.stateId" is "DELETED"
And store "$.payload.mutatedEntity.currentState.stateId" from response to "finalState"
