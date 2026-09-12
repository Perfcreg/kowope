package com.uba.mbp.integration.writeoffdetection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.uba.mbp")
public class WriteOffDetectionServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(WriteOffDetectionServiceApplication.class, args);
    }
}
