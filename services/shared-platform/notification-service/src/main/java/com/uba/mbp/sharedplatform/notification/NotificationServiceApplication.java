package com.uba.mbp.sharedplatform.notification;

import com.uba.mbp.sharedplatform.notification.config.NotificationRecipientsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.uba.mbp")
@EnableConfigurationProperties(NotificationRecipientsProperties.class)
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
