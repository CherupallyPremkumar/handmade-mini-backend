package com.pochampally.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "banners", schema = "homebase_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(length = 200)
    private String title;

    @Column(length = 500)
    private String subtitle;

    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;

    @Column(name = "mobile_image_url", length = 1000)
    private String mobileImageUrl;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "link_text", length = 100)
    private String linkText;

    @Column(name = "text_color", length = 20)
    @Builder.Default
    private String textColor = "#FFFFFF";

    @Column(nullable = false)
    @Builder.Default
    private Integer position = 0;

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
