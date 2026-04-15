package com.unios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@PropertySource(value = "classpath:secrets.properties", ignoreResourceNotFound = true)
public class UniOsApplication {
    public static void main(String[] args) {
        SpringApplication.run(UniOsApplication.class, args);
    }
}