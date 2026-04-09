package com.pochampally.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "cart_items", schema = "homebase_db", indexes = {
        @Index(name = "idx_cart_items_session_id", columnList = "session_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "user_id")
    private String userId;

    /** Selling price snapshot at time item was added (paisa). Null for legacy items. */
    @Column(name = "snapshot_price")
    private Long snapshotPrice;

    /** MRP snapshot at time item was added (paisa). Null for legacy items. */
    @Column(name = "snapshot_mrp")
    private Long snapshotMrp;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @PrePersist
    protected void onCreate() {
        if (addedAt == null) {
            addedAt = Instant.now();
        }
    }
}
