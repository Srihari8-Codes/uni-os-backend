package com.unios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@PropertySource("classpath:secrets.properties")
public class UniOsApplication {
    public static void main(String[] args) {
        SpringApplication.run(UniOsApplication.class, args);
    }
}