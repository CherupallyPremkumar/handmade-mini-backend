Feature: Tests the Product Query Service using a REST client. 

Scenario: Tests out pagination capability
When I POST a REST request to URL "/q/products" with payload
"""
{
	"sortCriteria" :[
		{"name":"name","ascendingOrder": true}
	],
	"pageNum": 2,
	"numRowsInPage": 15
}
"""
Then the http status code is 200
And the top level code is 200
And success is true 
And the REST response key "numRowsReturned" is "15"
And the REST response key "currentPage" is "2"
And the REST response key "maxPages" is "2"
And the REST response key "list[0].row.name" is "Narendra"
And the REST response key "list[0].row.id" is "25"
And the REST response key "list[14].row.name" is "Vikas"
And the REST response key "list[14].row.id" is "18"

Scenario: Test Likes query
When I POST a REST request to URL "/q/products" with payload
"""
{
	"filters" :{
		"search": "ja"
	}
}
"""
Then the http status code is 200
And the top level code is 200
And success is true 
And the REST response key "numRowsReturned" is "1"
And the REST response key "list[0].row.name" is "Vijay"
And the REST response key "list[0].row.id" is "29"

Scenario: Test Specific - Test Filter by Fabric and WeaveType
When I POST a REST request to URL "/q/products" with payload
"""
{
	"filters" :{
		"fabric": "COTTON",
		"weaveType": "IKAT"
	},
	"sortCriteria" :[
		{"name":"name","ascendingOrder": true}
	]
}
"""
Then the http status code is 200
And the top level code is 200
And success is true
And the REST response key "numRowsReturned" is "9"
And the REST response key "list[0].row.name" is "Akash"
And the REST response key "list[0].row.id" is "5"
And the REST response key "list[1].row.name" is "Ayush"
And the REST response key "list[1].row.id" is "20"
