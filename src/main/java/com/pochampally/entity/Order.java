package com.pochampally.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "orders", schema = "homebase_db", indexes = {
        @Index(name = "idx_orders_order_number", columnList = "order_number", unique = true),
        @Index(name = "idx_orders_customer_email", columnList = "customer_email"),
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_razorpay_oid", columnList = "razorpay_order_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "order_number", nullable = false, unique = true, length = 40)
    private String orderNumber;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 15)
    private String customerPhone;

    @Column(name = "customer_email")
    private String customerEmail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> shippingAddress;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false)
    private Long subtotal;

    @Column(name = "gst_amount", nullable = false)
    private Long gstAmount;

    @Column(name = "shipping_cost", nullable = false)
    private Long shippingCost;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "payment_status", length = 30)
    private String paymentStatus;

    @Column(name = "razorpay_order_id", length = 50)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 50)
    private String razorpayPaymentId;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PLACED;

    @Column(name = "tracking_number", length = 50)
    private String trackingNumber;

    @Column(name = "created_time", nullable = false, updatable = false)
    private Instant createdTime;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (createdTime == null) {
            createdTime = Instant.now();
        }
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public enum OrderStatus {
        PENDING_PAYMENT, PLACED, PAID, SHIPPED, DELIVERED, CANCELLED
    }
}
