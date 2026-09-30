package com.acespade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AceSpadeApplication {
    public static void main(String[] args) {
        SpringApplication.run(AceSpadeApplication.class, args);
    }
}
