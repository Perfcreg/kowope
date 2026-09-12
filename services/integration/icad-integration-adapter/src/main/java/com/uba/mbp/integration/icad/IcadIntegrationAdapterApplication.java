package com.uba.mbp.integration.icad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.uba.mbp")
public class IcadIntegrationAdapterApplication {
    public static void main(String[] args) {
        SpringApplication.run(IcadIntegrationAdapterApplication.class, args);
    }
}
