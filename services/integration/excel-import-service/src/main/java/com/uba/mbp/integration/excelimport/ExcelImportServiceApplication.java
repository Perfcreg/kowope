package com.uba.mbp.integration.excelimport;

import com.uba.mbp.integration.excelimport.config.NotificationServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.uba.mbp")
@EnableConfigurationProperties(NotificationServiceProperties.class)
public class ExcelImportServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExcelImportServiceApplication.class, args);
    }
}
