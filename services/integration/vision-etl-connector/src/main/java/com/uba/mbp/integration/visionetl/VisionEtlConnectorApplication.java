package com.uba.mbp.integration.visionetl;

import com.uba.mbp.integration.visionetl.config.FineractProperties;
import com.uba.mbp.integration.visionetl.config.NotificationServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.uba.mbp")
@EnableConfigurationProperties({FineractProperties.class, NotificationServiceProperties.class})
public class VisionEtlConnectorApplication {
    public static void main(String[] args) {
        SpringApplication.run(VisionEtlConnectorApplication.class, args);
    }
}
