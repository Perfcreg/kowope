package com.uba.mbp.sharedplatform.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.uba.mbp")
public class AuthenticationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthenticationServiceApplication.class, args);
    }
}
