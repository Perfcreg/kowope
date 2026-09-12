package com.uba.mbp.referencedataconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.uba.mbp")
public class ReferenceDataConfigApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReferenceDataConfigApplication.class, args);
    }
}
