package com.uba.mbp.accountverification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.uba.mbp")
public class AccountVerificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccountVerificationApplication.class, args);
    }
}
