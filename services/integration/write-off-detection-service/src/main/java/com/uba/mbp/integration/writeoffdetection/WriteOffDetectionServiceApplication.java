package com.uba.mbp.integration.writeoffdetection;

import com.uba.mbp.integration.writeoffdetection.config.FineractProperties;
import com.uba.mbp.integration.writeoffdetection.config.WriteOffDetectionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.uba.mbp")
@EnableConfigurationProperties({FineractProperties.class, WriteOffDetectionProperties.class})
public class WriteOffDetectionServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(WriteOffDetectionServiceApplication.class, args);
    }
}
