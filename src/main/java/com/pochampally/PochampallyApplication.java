package com.pochampally;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.pochampally", "com.homebase.mini", "org.chenile"})
@EnableScheduling
@EnableJpaRepositories(basePackages = {"com.pochampally.repository", "com.homebase.mini.product.configuration.dao"})
@EntityScan(basePackages = {"com.pochampally.entity", "com.homebase.mini.product.model"})
public class PochampallyApplication {

    static {
        System.setProperty("spring.classformat.ignore", "true");
    }

    public static void main(String[] args) {
        SpringApplication.run(PochampallyApplication.class, args);
    }
}
