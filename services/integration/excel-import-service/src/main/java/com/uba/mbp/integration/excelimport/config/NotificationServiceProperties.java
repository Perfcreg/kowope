package com.uba.mbp.integration.excelimport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Connection details for shared-platform's notification-service (ADR-0019). */
@ConfigurationProperties(prefix = "notification.service")
public class NotificationServiceProperties {

    private String baseUrl = "http://localhost:8086";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
