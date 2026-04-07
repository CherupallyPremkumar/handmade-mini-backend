package com.pochampally.bdd;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.*;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class Steps {

    @Autowired World w;

    // ═══════════════════════════════════════════════
    //  SETUP
    // ═══════════════════════════════════════════════

    @Given("the following products exist:")
    public void productsExist(DataTable table) {
        w.createProducts(table.asMaps());
    }

    @Given("a registered user with email {string} and password {string}")
    public void registeredUser(String email, String pw) throws Exception {
        w.post("/api/auth/register",
                """
                {"name":"Test User","email":"%s","password":"%s"}
                """.formatted(email, pw));
    }

    @Given("I am logged in as {string} with password {string}")
    public void loggedIn(String email, String pw) throws Exception {
        w.loginAs(email, pw);
    }

    @Given("I am logged in as admin")
    public void loggedInAsAdmin() throws Exception {
        w.loginAsAdmin();
    }

    @Given("I am logged in as customer")
    public void loggedInAsCustomer() throws Exception {
        w.loginAs("customer@test.com", "Customer@123");
    }

    @Given("I have placed an order for {string} quantity {int}")
    public void placedOrder(String product, int qty) throws Exception {
        w.placeOrder(product, qty);
    }

    // ═══════════════════════════════════════════════
    //  AUTH
    // ═══════════════════════════════════════════════

    @When("I register with name {string} email {string} and password {string}")
    public void register(String name, String email, String pw) throws Exception {
        w.post("/api/auth/register",
                """
                {"name":"%s","email":"%s","password":"%s"}
                """.formatted(name, email, pw));
    }

    @When("I login with email {string} and password {string}")
    public void login(String email, String pw) throws Exception {
        w.post("/api/auth/login",
                """
                {"email":"%s","password":"%s"}
                """.formatted(email, pw));
    }

    @Then("the response contains a JWT token")
    public void hasToken() throws Exception {
        assertThat(w.jsonKey("token")).isNotNull().contains(".");
    }

    @Then("the response has name {string}")
    public void hasName(String name) throws Exception {
        assertThat(w.jsonKey("name")).isEqualTo(name);
    }

    @Then("the response has role {string}")
    public void hasRole(String role) throws Exception {
        assertThat(w.jsonKey("role")).isEqualTo(role);
    }

    // ═══════════════════════════════════════════════
    //  GENERIC HTTP
    // ═══════════════════════════════════════════════

    @When("I POST {string} with body:")
    public void postBody(String url, String body) throws Exception {
        w.post(url, body);
    }

    @When("I POST {string} with auth and body:")
    public void postAuthBody(String url, String body) throws Exception {
        w.postAuth(url, body);
    }

    @When("I GET {string}")
    public void getUrl(String url) throws Exception {
        w.get(url);
    }

    @When("I GET {string} without auth")
    public void getNoAuth(String url) throws Exception {
        w.get(url);
    }

    @When("I GET {string} with auth token")
    public void getWithAuth(String url) throws Exception {
        w.getAuth(url);
    }

    @When("I DELETE {string}")
    public void deleteUrl(String url) throws Exception {
        w.delete(url);
    }

    // ═══════════════════════════════════════════════
    //  PRODUCTS
    // ═══════════════════════════════════════════════

    @When("I GET the first product by ID")
    public void getFirstProduct() throws Exception {
        w.get("/api/products/" + w.firstProductId());
    }

    @When("I update the first product name to {string}")
    public void updateProduct(String newName) throws Exception {
        String id = w.firstProductId();
        var r = w.get("/api/products/" + id);
        String body = r.getResponse().getContentAsString()
                .replaceFirst("\"name\":\"[^\"]*\"", "\"name\":\"" + newName + "\"");
        w.putAuth("/api/admin/products/" + id, body);
    }

    @When("I DELETE the first product with auth")
    public void deleteProduct() throws Exception {
        w.deleteAuth("/api/admin/products/" + w.firstProductId());
    }

    @Then("the deleted product is no longer in active list")
    public void deletedNotInList() throws Exception {
        w.get("/api/products");
        assertThat(w.body()).doesNotContain(w.firstProductId());
    }

    @Then("product {string} is not in the list")
    public void productNotInList(String name) throws Exception {
        for (JsonNode n : w.jsonBody()) {
            assertThat(n.get("name").asText()).isNotEqualTo(name);
        }
    }

    @Then("the response list contains product {string}")
    public void listContains(String name) throws Exception {
        boolean found = false;
        for (JsonNode n : w.jsonBody()) {
            if (name.equals(n.get("name").asText())) { found = true; break; }
        }
        assertThat(found).as("Expected '%s' in list", name).isTrue();
    }

    @Given("product {string} has stock {int}")
    public void productHasStock(String name, int stock) {
        var p = w.products.findById(w.productId(name)).orElseThrow();
        assertThat(p.getStock()).isGreaterThanOrEqualTo(stock);
    }

    @Then("product {string} now has stock {int}")
    public void productStockIs(String name, int expected) {
        int actual = w.products.findById(w.productId(name)).orElseThrow().getStock();
        assertThat(actual).isEqualTo(expected);
    }

    // ═══════════════════════════════════════════════
    //  CART
    // ═══════════════════════════════════════════════

    @When("I add product {string} to cart {string} with quantity {int}")
    public void addToCart(String product, String session, int qty) throws Exception {
        w.post("/api/cart/" + session + "/add",
                """
                {"productId":"%s","quantity":%d}
                """.formatted(w.productId(product), qty));
    }

    @When("I store the cart item ID")
    public void storeCartItem() throws Exception {
        w.savedCartItemId = w.jsonKey("id");
    }

    @When("I remove the stored item from cart {string}")
    public void removeCartItem(String session) throws Exception {
        w.delete("/api/cart/" + session + "/" + w.savedCartItemId);
    }

    @When("I try to remove the stored item from cart {string}")
    public void tryRemoveWrongSession(String session) throws Exception {
        w.delete("/api/cart/" + session + "/" + w.savedCartItemId);
    }

    @Then("cart {string} is empty")
    public void cartEmpty(String session) throws Exception {
        w.get("/api/cart/" + session);
        assertThat(w.jsonBody().size()).isEqualTo(0);
    }

    // ═══════════════════════════════════════════════
    //  CHECKOUT
    // ═══════════════════════════════════════════════

    @When("I create an order with auth for:")
    public void createOrder(DataTable table) throws Exception {
        StringBuilder items = new StringBuilder("[");
        for (var row : table.asMaps()) {
            if (items.length() > 1) items.append(",");
            items.append("""
                {"productId":"%s","quantity":%s}
                """.formatted(w.productId(row.get("productName")), row.get("quantity")));
        }
        items.append("]");

        w.postAuth("/api/checkout/create-order", """
            {
                "customerName":"Test Buyer","customerPhone":"+919876543210",
                "customerEmail":"buyer@test.com",
                "shippingAddress":{"line1":"123 St","city":"Hyderabad","state":"Telangana","pincode":"500001"},
                "items":%s
            }
            """.formatted(items));

        if (w.status() == 200) {
            w.savedOrderNumber = w.jsonKey("orderNumber");
            var order = w.orders.findByOrderNumber(w.savedOrderNumber).orElse(null);
            w.savedOrderId = order != null ? order.getId() : null;
            w.savedOrderAmount = w.jsonKeyLong("amount");
        }
    }

    @Then("the order amount includes {int} percent GST on {long} paisa")
    public void gstIncluded(int pct, long subtotal) {
        long gst = (subtotal * pct) / 100;
        assertThat(w.savedOrderAmount).isGreaterThanOrEqualTo(subtotal + gst);
    }

    @Then("the order has free shipping")
    public void freeShipping() {
        // 10 * 100000 = 1000000 subtotal + 5% GST = 1050000, no shipping
        assertThat(w.savedOrderAmount).isEqualTo(1050000);
    }

    @Then("the order has shipping cost of {long} paisa")
    public void hasShipping(long shipping) {
        // 50000 subtotal + 2500 GST + 9900 shipping = 62400
        assertThat(w.savedOrderAmount).isEqualTo(50000 + 2500 + shipping);
    }

    // ═══════════════════════════════════════════════
    //  ORDERS
    // ═══════════════════════════════════════════════

    @When("I GET the order by order number without auth")
    public void getOrderPublic() throws Exception {
        w.get("/api/orders/" + w.savedOrderNumber);
    }

    @Then("the tracking response has order number")
    public void trackingOrderNumber() throws Exception {
        assertThat(w.jsonKey("orderNumber")).startsWith("DHN-");
    }

    @Then("the tracking response has status {string}")
    public void trackingStatus(String status) throws Exception {
        assertThat(w.jsonKey("status")).isEqualTo(status);
    }

    @Then("the tracking response hides customer PII")
    public void trackingNosPii() throws Exception {
        String body = w.body();
        assertThat(body).doesNotContain("9876543210");
        assertThat(body).doesNotContain("buyer@test.com");
    }

    @When("I update order status to {string}")
    public void updateStatus(String status) throws Exception {
        w.patchAuth("/api/admin/orders/" + w.savedOrderId + "/status",
                """
                {"status":"%s"}
                """.formatted(status));
    }

    @When("I update order status to {string} with tracking {string}")
    public void updateStatusTracking(String status, String tracking) throws Exception {
        w.patchAuth("/api/admin/orders/" + w.savedOrderId + "/status",
                """
                {"status":"%s","trackingNumber":"%s"}
                """.formatted(status, tracking));
    }

    // ═══════════════════════════════════════════════
    //  GENERIC ASSERTIONS
    // ═══════════════════════════════════════════════

    @Then("the response status is {int}")
    public void statusIs(int expected) {
        assertThat(w.status()).isEqualTo(expected);
    }

    @Then("the response error contains {string}")
    public void errorContains(String text) throws Exception {
        assertThat(w.body()).containsIgnoringCase(text);
    }

    @Then("the response JSON key {string} is {string}")
    public void jsonStr(String key, String expected) throws Exception {
        assertThat(w.jsonKey(key)).isEqualTo(expected);
    }

    @Then("the response JSON key {string} is {int}")
    public void jsonInt(String key, int expected) throws Exception {
        assertThat(w.jsonKeyInt(key)).isEqualTo(expected);
    }

    @Then("the response JSON key {string} is not empty")
    public void jsonNotEmpty(String key) throws Exception {
        assertThat(w.jsonKey(key)).isNotNull().isNotEmpty();
    }

    @Then("the response JSON key {string} starts with {string}")
    public void jsonStartsWith(String key, String prefix) throws Exception {
        assertThat(w.jsonKey(key)).startsWith(prefix);
    }

    @Then("the response is a list of {int} products")
    public void listSize(int size) throws Exception {
        assertThat(w.jsonBody().isArray()).isTrue();
        assertThat(w.jsonBody().size()).isEqualTo(size);
    }

    @Then("the response is a list of {int} items")
    public void listItems(int size) throws Exception {
        assertThat(w.jsonBody().isArray()).isTrue();
        assertThat(w.jsonBody().size()).isEqualTo(size);
    }

    @Then("the response is an empty list")
    public void emptyList() throws Exception {
        assertThat(w.jsonBody().isArray()).isTrue();
        assertThat(w.jsonBody().size()).isEqualTo(0);
    }

    // ═══════════════════════════════════════════════
    //  PER-PRODUCT GST
    // ═══════════════════════════════════════════════

    @Then("the order status in DB is {string}")
    public void orderStatusInDb(String status) {
        var order = w.orders.findById(w.savedOrderId).orElseThrow();
        assertThat(order.getStatus().name()).isEqualTo(status);
    }

    @Then("the order GST is {long} paisa for subtotal {long} paisa")
    public void orderGst(long expectedGst, long expectedSubtotal) throws Exception {
        // Silk 5%: 100000 * 5% = 5000
        // Cotton 12%: 200000 * 12% = 24000
        // Total GST = 29000
        var order = w.orders.findById(w.savedOrderId).orElseThrow();
        assertThat(order.getSubtotal()).isEqualTo(expectedSubtotal);
        assertThat(order.getGstAmount()).isEqualTo(expectedGst);
    }

    // ═══════════════════════════════════════════════
    //  RAZORPAY WEBHOOKS
    // ═══════════════════════════════════════════════

    @When("I send a Razorpay webhook {string} with valid signature")
    public void webhookValid(String event) throws Exception {
        String razorpayOrderId = w.orders.findById(w.savedOrderId).orElseThrow().getRazorpayOrderId();
        String body = webhookPayload(event, razorpayOrderId, "pay_test_123");
        // Our test RazorpayService.verifyWebhookSignature returns true for sig="valid"
        w.call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/webhooks/razorpay")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", "valid")
                .content(body));
    }

    @When("I send a Razorpay webhook {string} with invalid signature")
    public void webhookInvalid(String event) throws Exception {
        String body = webhookPayload(event, "order_fake", "pay_fake");
        w.call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/webhooks/razorpay")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", "bad_sig")
                .content(body));
    }

    @When("I send a Razorpay webhook {string} without signature")
    public void webhookNoSig(String event) throws Exception {
        String body = webhookPayload(event, "order_fake", "pay_fake");
        w.call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/webhooks/razorpay")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Then("the order status is now {string}")
    public void orderStatusIs(String status) {
        var order = w.orders.findById(w.savedOrderId).orElseThrow();
        assertThat(order.getStatus().name()).isEqualTo(status);
    }

    @Then("the order payment status is {string}")
    public void orderPaymentStatus(String status) {
        var order = w.orders.findById(w.savedOrderId).orElseThrow();
        assertThat(order.getPaymentStatus()).isEqualTo(status);
    }

    private String webhookPayload(String event, String orderId, String paymentId) {
        return """
            {
                "event":"%s",
                "payload":{
                    "payment":{
                        "entity":{
                            "id":"%s",
                            "order_id":"%s",
                            "amount":100000,
                            "currency":"INR",
                            "status":"captured"
                        }
                    }
                }
            }
            """.formatted(event, paymentId, orderId);
    }

    // ═══════════════════════════════════════════════
    //  OPTIMISTIC LOCKING
    // ═══════════════════════════════════════════════

    @When("I DELETE {string} with auth")
    public void deleteWithAuth(String url) throws Exception {
        w.deleteAuth(url);
    }

    @When("I PUT {string} with auth and body:")
    public void putWithAuthBody(String url, String body) throws Exception {
        w.putAuth(url, body);
    }

    @When("I PATCH {string} with auth and body:")
    public void patchWithAuthBody(String url, String body) throws Exception {
        String resolved = url.replace("{savedOrderId}", w.savedOrderId != null ? w.savedOrderId : "null");
        w.patchAuth(resolved, body);
    }

    @When("I POST {string} with webhook signature {string} and body:")
    public void postWebhookWithSig(String url, String sig, String body) throws Exception {
        w.call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(url)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", sig)
                .content(body));
    }

    @When("I update product {string} with stale version")
    public void updateWithStaleVersion(String name) throws Exception {
        String id = w.productId(name);
        var product = w.products.findById(id).orElseThrow();

        // Simulate stale version: update product to bump version
        product.setStock(product.getStock() - 1);
        w.products.saveAndFlush(product);

        // Now send update with old version (0)
        String body = """
            {"name":"%s","fabric":"SILK","weaveType":"IKAT","color":"Blue",
             "sellingPrice":100000,"mrp":150000,"stock":5,"version":0,
             "gstPct":5,"hsnCode":"50079090"}
            """.formatted(name);
        w.putAuth("/api/admin/products/" + id, body);
    }
}
