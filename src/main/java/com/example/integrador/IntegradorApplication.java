package com.example.integrador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IntegradorApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntegradorApplication.class, args);
    }

}

