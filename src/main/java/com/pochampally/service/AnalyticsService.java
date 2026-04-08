package com.pochampally.service;

import com.pochampally.entity.Order;
import com.pochampally.entity.Product;
import com.pochampally.repository.OrderRepository;
import com.pochampally.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    private static final Set<Order.OrderStatus> REVENUE_STATUSES = Set.of(
            Order.OrderStatus.PAID, Order.OrderStatus.SHIPPED, Order.OrderStatus.DELIVERED);

    public Map<String, Object> getDashboard() {
        Instant now = Instant.now();
        Instant todayStart = now.truncatedTo(ChronoUnit.DAYS);
        Instant weekStart = now.minus(7, ChronoUnit.DAYS);
        Instant monthStart = now.minus(30, ChronoUnit.DAYS);

        List<Order> allOrders = orderRepository.findAll();

        long revenueToday = revenue(allOrders, todayStart);
        long revenueWeek = revenue(allOrders, weekStart);
        long revenueMonth = revenue(allOrders, monthStart);
        long ordersToday = allOrders.stream()
                .filter(o -> o.getCreatedTime().isAfter(todayStart)).count();

        // Orders by status
        Map<String, Long> ordersByStatus = allOrders.stream()
                .collect(Collectors.groupingBy(o -> o.getStatus().name(), Collectors.counting()));

        // Top 5 selling products (by quantity from paid orders)
        Map<String, long[]> productSales = new LinkedHashMap<>();
        allOrders.stream()
                .filter(o -> REVENUE_STATUSES.contains(o.getStatus()))
                .flatMap(o -> o.getItems().stream())
                .forEach(item -> {
                    productSales.computeIfAbsent(item.getProductName(), k -> new long[2]);
                    productSales.get(item.getProductName())[0] += item.getQuantity();
                    productSales.get(item.getProductName())[1] += item.getTotalPrice();
                });

        List<Map<String, Object>> topProducts = productSales.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(5)
                .map(e -> Map.<String, Object>of(
                        "name", e.getKey(),
                        "quantity", e.getValue()[0],
                        "revenue", e.getValue()[1]))
                .toList();

        // Recent 10 orders
        List<Map<String, Object>> recentOrders = allOrders.stream()
                .sorted(Comparator.comparing(Order::getCreatedTime).reversed())
                .limit(10)
                .map(o -> Map.<String, Object>of(
                        "orderNumber", o.getOrderNumber(),
                        "customerName", o.getCustomerName() != null ? o.getCustomerName() : "",
                        "status", o.getStatus().name(),
                        "totalAmount", o.getTotalAmount(),
                        "createdTime", o.getCreatedTime().toString()))
                .toList();

        // Low stock (threshold from settings or default 3)
        List<Map<String, Object>> lowStock = productRepository.findByIsActiveTrue().stream()
                .filter(p -> p.getStock() <= 3)
                .map(p -> Map.<String, Object>of("name", p.getName(), "stock", p.getStock(), "sku", p.getSku() != null ? p.getSku() : ""))
                .toList();

        return Map.of(
                "revenueToday", revenueToday,
                "revenueThisWeek", revenueWeek,
                "revenueThisMonth", revenueMonth,
                "ordersToday", ordersToday,
                "totalOrders", allOrders.size(),
                "ordersByStatus", ordersByStatus,
                "topProducts", topProducts,
                "recentOrders", recentOrders,
                "lowStockProducts", lowStock
        );
    }

    private long revenue(List<Order> orders, Instant since) {
        return orders.stream()
                .filter(o -> REVENUE_STATUSES.contains(o.getStatus()))
                .filter(o -> o.getCreatedTime().isAfter(since))
                .mapToLong(Order::getTotalAmount)
                .sum();
    }
}
