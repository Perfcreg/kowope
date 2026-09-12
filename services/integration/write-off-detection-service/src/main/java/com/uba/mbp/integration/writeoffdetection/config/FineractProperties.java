package com.uba.mbp.integration.writeoffdetection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection details for the Fineract instance standing in for Finacle (ADR-0012).
 * Defaults match mfb-stack's own committed dev values — not a secret this
 * project introduces.
 */
@ConfigurationProperties(prefix = "fineract")
public class FineractProperties {

    private String baseUrl = "http://localhost:8444";
    private String tenant = "default";
    private String username = "mifos";
    private String password = "password";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
