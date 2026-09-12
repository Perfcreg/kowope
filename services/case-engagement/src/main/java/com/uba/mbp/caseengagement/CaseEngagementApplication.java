package com.uba.mbp.caseengagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.uba.mbp")
public class CaseEngagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(CaseEngagementApplication.class, args);
    }
}
