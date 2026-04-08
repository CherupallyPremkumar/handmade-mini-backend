package com.pochampally.service;

import com.pochampally.entity.Order;
import com.pochampally.repository.OrderRepository;
import com.pochampally.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

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

        // Targeted DB queries — no findAll()
        long revenueToday = orderRepository.sumRevenueByStatusesAndCreatedTimeAfter(REVENUE_STATUSES, todayStart);
        long revenueWeek = orderRepository.sumRevenueByStatusesAndCreatedTimeAfter(REVENUE_STATUSES, weekStart);
        long revenueMonth = orderRepository.sumRevenueByStatusesAndCreatedTimeAfter(REVENUE_STATUSES, monthStart);
        long ordersToday = orderRepository.countByCreatedTimeAfter(todayStart);
        long totalOrders = orderRepository.count();

        // Orders by status
        Map<String, Long> ordersByStatus = new LinkedHashMap<>();
        for (Order.OrderStatus s : Order.OrderStatus.values()) {
            long count = orderRepository.findByStatus(s).size();
            if (count > 0) ordersByStatus.put(s.name(), count);
        }

        // Recent 10 orders (lightweight)
        List<Order> recent = orderRepository.findTop10ByOrderByCreatedTimeDesc();
        List<Map<String, Object>> recentOrders = recent.stream()
                .map(o -> Map.<String, Object>of(
                        "orderNumber", o.getOrderNumber(),
                        "customerName", o.getCustomerName() != null ? o.getCustomerName() : "",
                        "status", o.getStatus().name(),
                        "totalAmount", o.getTotalAmount(),
                        "createdTime", o.getCreatedTime().toString()))
                .toList();

        // Low stock
        List<Map<String, Object>> lowStock = productRepository.findByIsActiveTrue().stream()
                .filter(p -> p.getStock() <= 3)
                .map(p -> Map.<String, Object>of("name", p.getName(), "stock", p.getStock(), "sku", p.getSku() != null ? p.getSku() : ""))
                .toList();

        return Map.of(
                "revenueToday", revenueToday,
                "revenueThisWeek", revenueWeek,
                "revenueThisMonth", revenueMonth,
                "ordersToday", ordersToday,
                "totalOrders", totalOrders,
                "ordersByStatus", ordersByStatus,
                "topProducts", List.of(), // TODO: add aggregate query
                "recentOrders", recentOrders,
                "lowStockProducts", lowStock
        );
    }
}
