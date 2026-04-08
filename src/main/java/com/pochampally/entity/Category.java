package com.pochampally.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "categories", schema = "homebase_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(length = 500)
    private String description;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    /** Maps to product filter — e.g., filterKey=fabric, filterValue=SILK */
    @Column(name = "filter_key", length = 50)
    private String filterKey;

    @Column(name = "filter_value", length = 50)
    private String filterValue;

    @Column(nullable = false)
    @Builder.Default
    private Integer position = 0;

    @Column(name = "show_on_home", nullable = false)
    @Builder.Default
    private Boolean showOnHome = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_time", nullable = false, updatable = false)
    private Instant createdTime;

    @PrePersist
    protected void onCreate() {
        if (createdTime == null) createdTime = Instant.now();
    }
}
