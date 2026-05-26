package com.homebase.mini.product.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.chenile.jpautils.entity.AbstractJpaStateEntity;
import org.chenile.workflow.model.ContainsTransientMap;
import org.chenile.workflow.model.TransientMap;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "products", schema = "homebase_db", indexes = {
        @Index(name = "idx_products_category", columnList = "category"),
        @Index(name = "idx_products_fabric", columnList = "fabric"),
        @Index(name = "idx_products_is_active", columnList = "is_active")
})
@AttributeOverrides({
        @AttributeOverride(name = "slaLate", column = @Column(name = "sla_late", nullable = true)),
        @AttributeOverride(name = "slaTendingLate", column = @Column(name = "sla_tending_late", nullable = true)),
        @AttributeOverride(name = "testEntity", column = @Column(name = "test_entity", nullable = true))
})
public class Product extends AbstractJpaStateEntity implements ContainsTransientMap {

    @Transient
    public transient TransientMap transientMap = new TransientMap();

    @Override
    public TransientMap getTransientMap() {
        if (this.transientMap == null) {
            this.transientMap = new TransientMap();
        }
        return this.transientMap;
    }

    @NotBlank(message = "Product name is required")
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "secondary_description", columnDefinition = "TEXT")
    private String secondaryDescription;

    // Clothing category
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category = Category.SAREE;

    // Fabric & weave (textile-specific)
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Fabric fabric;

    @Enumerated(EnumType.STRING)
    @Column(name = "weave_type", length = 30)
    private WeaveType weaveType;

    @Column(length = 50)
    private String size;

    @Column(name = "length_meters")
    private Double lengthMeters;

    @Column(name = "blouse_piece")
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

    @Enumerated(EnumType.STRING)
    @Column(name = "video_status", length = 20)
    private VideoStatus videoStatus = VideoStatus.NONE;

    // Tax
    @Column(name = "hsn_code", nullable = false, length = 20)
    private String hsnCode = "50079090";

    @Column(name = "gst_pct", nullable = false)
    private Integer gstPct = 5;

    // Ratings (cached from reviews)
    @Column(name = "average_rating")
    private Double averageRating;

    @Column(name = "review_count")
    private Integer reviewCount = 0;

    // Physical attributes
    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Column(name = "width_inches")
    private Double widthInches;

    @Column(name = "blouse_length_meters")
    private Double blouseLengthMeters;

    // Classification
    @Column(length = 200)
    private String occasion;

    @Column(name = "work_type", length = 200)
    private String workType;

    @Column(length = 200)
    private String pattern;

    // Color breakdown
    @Column(name = "body_color", length = 100)
    private String bodyColor;

    @Column(name = "border_color", length = 100)
    private String borderColor;

    @Column(name = "pallu_color", length = 100)
    private String palluColor;

    // Details
    @Column(name = "care_instructions", length = 500)
    private String careInstructions;

    @Column(length = 200)
    private String certification;

    @Column(length = 50, unique = true, nullable = false)
    private String sku;

    // SEO tags (comma-separated)
    @Column(length = 500)
    private String tags;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSecondaryDescription() { return secondaryDescription; }
    public void setSecondaryDescription(String secondaryDescription) { this.secondaryDescription = secondaryDescription; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public Fabric getFabric() { return fabric; }
    public void setFabric(Fabric fabric) { this.fabric = fabric; }

    public WeaveType getWeaveType() { return weaveType; }
    public void setWeaveType(WeaveType weaveType) { this.weaveType = weaveType; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public Double getLengthMeters() { return lengthMeters; }
    public void setLengthMeters(Double lengthMeters) { this.lengthMeters = lengthMeters; }

    public Boolean getBlousePiece() { return blousePiece; }
    public void setBlousePiece(Boolean blousePiece) { this.blousePiece = blousePiece; }

    public Long getMrp() { return mrp; }
    public void setMrp(Long mrp) { this.mrp = mrp; }

    public Long getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(Long sellingPrice) { this.sellingPrice = sellingPrice; }

    public Integer getDiscountPct() { return discountPct; }
    public void setDiscountPct(Integer discountPct) { this.discountPct = discountPct; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public VideoStatus getVideoStatus() { return videoStatus; }
    public void setVideoStatus(VideoStatus videoStatus) { this.videoStatus = videoStatus; }

    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }

    public Integer getGstPct() { return gstPct; }
    public void setGstPct(Integer gstPct) { this.gstPct = gstPct; }

    public Double getAverageRating() { return averageRating; }
    public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }

    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }

    public Integer getWeightGrams() { return weightGrams; }
    public void setWeightGrams(Integer weightGrams) { this.weightGrams = weightGrams; }

    public Double getWidthInches() { return widthInches; }
    public void setWidthInches(Double widthInches) { this.widthInches = widthInches; }

    public Double getBlouseLengthMeters() { return blouseLengthMeters; }
    public void setBlouseLengthMeters(Double blouseLengthMeters) { this.blouseLengthMeters = blouseLengthMeters; }

    public String getOccasion() { return occasion; }
    public void setOccasion(String occasion) { this.occasion = occasion; }

    public String getWorkType() { return workType; }
    public void setWorkType(String workType) { this.workType = workType; }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public String getBodyColor() { return bodyColor; }
    public void setBodyColor(String bodyColor) { this.bodyColor = bodyColor; }

    public String getBorderColor() { return borderColor; }
    public void setBorderColor(String borderColor) { this.borderColor = borderColor; }

    public String getPalluColor() { return palluColor; }
    public void setPalluColor(String palluColor) { this.palluColor = palluColor; }

    public String getCareInstructions() { return careInstructions; }
    public void setCareInstructions(String careInstructions) { this.careInstructions = careInstructions; }

    public String getCertification() { return certification; }
    public void setCertification(String certification) { this.certification = certification; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public enum Category {
        SAREE, DHOTI, DUPATTA, SHIRT, KURTA, LUNGI, FABRIC_PIECE, OTHER
    }

    public enum Fabric {
        SILK, COTTON, SILK_COTTON, LINEN, POLYESTER, GEORGETTE, CHIFFON
    }

    public enum WeaveType {
        IKAT, TELIA_RUMAL, MERCERIZED, HANDLOOM, POWERLOOM, JACQUARD, PLAIN
    }

    public enum VideoStatus {
        NONE, COMPRESSING, READY, FAILED
    }
}
