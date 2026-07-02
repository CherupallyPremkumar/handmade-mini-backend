package com.pochampally.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "policies", schema = "homebase_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Policy {

    @Id
    @Column(length = 50)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "meta_description", length = 500)
    private String metaDescription;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "updated_time", nullable = false)
    private Instant updatedTime;

    @PreUpdate
    @PrePersist
    protected void onSave() {
        updatedTime = Instant.now();
    }
}
