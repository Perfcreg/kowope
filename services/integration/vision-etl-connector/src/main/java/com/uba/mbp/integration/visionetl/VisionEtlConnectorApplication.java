package com.uba.mbp.integration.visionetl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.uba.mbp")
public class VisionEtlConnectorApplication {
    public static void main(String[] args) {
        SpringApplication.run(VisionEtlConnectorApplication.class, args);
    }
}
