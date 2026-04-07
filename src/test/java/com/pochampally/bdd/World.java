package com.pochampally.bdd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pochampally.config.LoginRateLimiter;
import com.pochampally.entity.Product;
import com.pochampally.entity.User;
import com.pochampally.repository.*;
import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Shared per-scenario state + reusable REST helpers.
 * Every scenario starts with a clean database and clean state.
 */
public class World {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired CartItemRepository cartItems;
    @Autowired LoginRateLimiter rateLimiter;

    // ── Per-scenario state ──
    MvcResult lastResult;
    String token;
    final Map<String, String> productIds = new LinkedHashMap<>();
    String savedCartItemId;
    String savedOrderId;
    String savedOrderNumber;
    long savedOrderAmount;

    @Before(order = 0)
    public void cleanDb() {
        rateLimiter.reset();
        orderItems.deleteAll();
        orders.deleteAll();
        cartItems.deleteAll();
        products.deleteAll();
        users.deleteAll();
    }

    @Before(order = 1)
    public void resetState() {
        lastResult = null;
        token = null;
        productIds.clear();
        savedCartItemId = null;
        savedOrderId = null;
        savedOrderNumber = null;
        savedOrderAmount = 0;
    }

    // ── REST helpers ──

    /** Execute request, store result. */
    MvcResult call(MockHttpServletRequestBuilder req) throws Exception {
        lastResult = mvc.perform(req).andReturn();
        return lastResult;
    }

    /** POST with JSON body, no auth. */
    MvcResult post(String url, String body) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** POST with JSON body + Bearer token. */
    MvcResult postAuth(String url, String body) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(url).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token).content(body));
    }

    /** GET, no auth. */
    MvcResult get(String url) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url));
    }

    /** GET with Bearer token. */
    MvcResult getAuth(String url) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get(url).header("Authorization", "Bearer " + token));
    }

    /** PUT with JSON body + Bearer token. */
    MvcResult putAuth(String url, String body) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put(url).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token).content(body));
    }

    /** PATCH with JSON body + Bearer token. */
    MvcResult patchAuth(String url, String body) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch(url).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token).content(body));
    }

    /** DELETE, no auth. */
    MvcResult delete(String url) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url));
    }

    /** DELETE with Bearer token. */
    MvcResult deleteAuth(String url) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete(url).header("Authorization", "Bearer " + token));
    }

    // ── JSON helpers ──

    int status() { return lastResult.getResponse().getStatus(); }

    String body() throws Exception { return lastResult.getResponse().getContentAsString(); }

    JsonNode jsonBody() throws Exception { return json.readTree(body()); }

    String jsonKey(String key) throws Exception {
        JsonNode node = jsonBody().get(key);
        return node == null ? null : node.asText();
    }

    int jsonKeyInt(String key) throws Exception { return jsonBody().get(key).asInt(); }

    long jsonKeyLong(String key) throws Exception { return jsonBody().get(key).asLong(); }

    String jsonKeyFrom(MvcResult r, String key) throws Exception {
        JsonNode node = json.readTree(r.getResponse().getContentAsString()).get(key);
        return node == null ? null : node.asText();
    }

    // ── Auth helpers ──

    /** Register + login, store token from cookie. Works even if email already registered. */
    void loginAs(String email, String password) throws Exception {
        post("/api/auth/register",
                """
                {"name":"Test User","email":"%s","password":"%s"}
                """.formatted(email, password));

        MvcResult r = post("/api/auth/login",
                """
                {"email":"%s","password":"%s"}
                """.formatted(email, password));

        // Extract token from httpOnly cookie
        jakarta.servlet.http.Cookie cookie = r.getResponse().getCookie("dhn_token");
        token = cookie != null ? cookie.getValue() : jsonKeyFrom(r, "token");
    }

    /** Register + login as admin (promotes role in DB). */
    void loginAsAdmin() throws Exception {
        String email = "admin-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        post("/api/auth/register",
                """
                {"name":"Admin","email":"%s","password":"Admin@123"}
                """.formatted(email));

        users.findByEmail(email).ifPresent(u -> {
            u.setRole(User.Role.ADMIN);
            users.save(u);
        });

        MvcResult r = post("/api/auth/login",
                """
                {"email":"%s","password":"Admin@123"}
                """.formatted(email));

        jakarta.servlet.http.Cookie cookie = r.getResponse().getCookie("dhn_token");
        token = cookie != null ? cookie.getValue() : jsonKeyFrom(r, "token");
    }

    // ── Product helpers ──

    void createProducts(List<Map<String, String>> rows) {
        for (Map<String, String> row : rows) {
            Product p = Product.builder()
                    .name(row.get("name"))
                    .fabric(Product.Fabric.valueOf(row.get("fabric")))
                    .weaveType(Product.WeaveType.valueOf(row.get("weaveType")))
                    .color(row.get("color"))
                    .sellingPrice(Long.parseLong(row.get("sellingPrice")))
                    .mrp(Long.parseLong(row.get("mrp")))
                    .stock(Integer.parseInt(row.get("stock")))
                    .gstPct(Integer.parseInt(row.getOrDefault("gstPct", "5")))
                    .hsnCode("50079090")
                    .isActive(!"Inactive Product".equals(row.get("name")))
                    .build();
            p = products.save(p);
            productIds.put(row.get("name"), p.getId());
        }
    }

    String productId(String name) {
        return productIds.computeIfAbsent(name, n ->
                products.findAll().stream()
                        .filter(p -> p.getName().equals(n))
                        .findFirst()
                        .map(Product::getId)
                        .orElseThrow(() -> new IllegalStateException("Product not found: " + n)));
    }

    String firstProductId() {
        return productIds.values().iterator().next();
    }

    // ── Order helpers ──

    /** Place an order (requires token set). Stores orderId, orderNumber, amount. */
    void placeOrder(String productName, int qty) throws Exception {
        String body = """
            {
                "customerName":"Test Buyer","customerPhone":"+919876543210",
                "customerEmail":"buyer@test.com",
                "shippingAddress":{"line1":"123 St","city":"Hyderabad","state":"Telangana","pincode":"500001"},
                "items":[{"productId":"%s","quantity":%d}]
            }
            """.formatted(productId(productName), qty);
        MvcResult r = postAuth("/api/checkout/create-order", body);
        savedOrderNumber = jsonKeyFrom(r, "orderNumber");
        // Look up order ID from DB by order number
        var order = orders.findByOrderNumber(savedOrderNumber).orElse(null);
        savedOrderId = order != null ? order.getId() : null;
        savedOrderAmount = json.readTree(r.getResponse().getContentAsString()).get("amount").asLong();
    }
}
