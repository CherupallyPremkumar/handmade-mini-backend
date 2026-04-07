package com.pochampally.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "products", schema = "homebase_db", indexes = {
        @Index(name = "idx_products_category", columnList = "category"),
        @Index(name = "idx_products_fabric", columnList = "fabric"),
        @Index(name = "idx_products_is_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank(message = "Product name is required")
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Clothing category
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Category category = Category.SAREE;

    // Fabric & weave (textile-specific)
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Fabric fabric;

    @Enumerated(EnumType.STRING)
    @Column(name = "weave_type", length = 30)
    private WeaveType weaveType;

    @Column(length = 50)
    private String color;

    @Column(length = 50)
    private String size;

    @Column(name = "length_meters")
    private Double lengthMeters;

    @Column(name = "blouse_piece")
    @Builder.Default
    private Boolean blousePiece = false;

    // Pricing (paisa)
    @NotNull(message = "MRP is required")
    @Min(value = 0, message = "MRP cannot be negative")
    @Column(nullable = false)
    private Long mrp;

    @NotNull(message = "Selling price is required")
    @Min(value = 0, message = "Selling price cannot be negative")
    @Column(name = "selling_price", nullable = false)
    private Long sellingPrice;

    @Column(name = "discount_pct")
    private Integer discountPct;

    @Column(nullable = false)
    private Integer stock;

    // Media
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> images;

    @Column(name = "video_url", length = 1000)
    private String videoUrl;

    // Tax
    @Column(name = "hsn_code", nullable = false, length = 20)
    @Builder.Default
    private String hsnCode = "50079090";

    @Column(name = "gst_pct", nullable = false)
    @Builder.Default
    private Integer gstPct = 5;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

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

    public enum Category {
        SAREE, DHOTI, DUPATTA, SHIRT, KURTA, LUNGI, FABRIC_PIECE, OTHER
    }

    public enum Fabric {
        SILK, COTTON, SILK_COTTON, LINEN, POLYESTER, GEORGETTE, CHIFFON
    }

    public enum WeaveType {
        IKAT, TELIA_RUMAL, MERCERIZED, HANDLOOM, POWERLOOM, JACQUARD, PLAIN
    }
}
