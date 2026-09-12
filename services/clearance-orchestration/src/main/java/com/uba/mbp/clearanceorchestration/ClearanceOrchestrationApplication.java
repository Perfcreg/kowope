package com.uba.mbp.clearanceorchestration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.uba.mbp")
public class ClearanceOrchestrationApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClearanceOrchestrationApplication.class, args);
    }
}
