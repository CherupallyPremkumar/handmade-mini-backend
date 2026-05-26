package com.pochampally.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper jackson2ObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure standard ObjectMapper settings
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try {
            // Dynamically register JavaTimeModule if present on classpath
            objectMapper.registerModule((com.fasterxml.jackson.databind.Module) 
                Class.forName("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule")
                     .getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            // Ignored if jsr310 is not on classpath
        }
        return objectMapper;
    }
}
