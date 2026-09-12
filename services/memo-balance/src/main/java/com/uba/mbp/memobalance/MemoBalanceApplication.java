package com.uba.mbp.memobalance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.uba.mbp")
public class MemoBalanceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MemoBalanceApplication.class, args);
    }
}
