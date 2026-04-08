package com.pochampally.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "app_settings", schema = "homebase_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 500)
    private String value;

    @Column(length = 500)
    private String description;
}
