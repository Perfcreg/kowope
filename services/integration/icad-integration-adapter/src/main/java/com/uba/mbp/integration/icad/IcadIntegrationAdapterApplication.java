package com.uba.mbp.integration.icad;

import com.uba.mbp.integration.icad.config.IcadProperties;
import com.uba.mbp.integration.icad.config.NotificationServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.uba.mbp")
@EnableConfigurationProperties({IcadProperties.class, NotificationServiceProperties.class})
public class IcadIntegrationAdapterApplication {
    public static void main(String[] args) {
        SpringApplication.run(IcadIntegrationAdapterApplication.class, args);
    }
}
