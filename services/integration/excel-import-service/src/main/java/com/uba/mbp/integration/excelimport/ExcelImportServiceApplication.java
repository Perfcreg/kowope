package com.uba.mbp.integration.excelimport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.uba.mbp")
public class ExcelImportServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExcelImportServiceApplication.class, args);
    }
}
