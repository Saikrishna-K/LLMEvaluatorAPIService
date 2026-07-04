package com.example.evaluator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EvaluatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(EvaluatorApplication.class, args);
    }
}
