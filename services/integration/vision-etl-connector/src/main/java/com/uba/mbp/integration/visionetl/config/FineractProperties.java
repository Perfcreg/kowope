package com.uba.mbp.integration.visionetl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection details for the Fineract instance standing in for Vision (ADR-0013).
 * Defaults match mfb-stack's own committed dev values — not a secret this
 * project introduces.
 */
@ConfigurationProperties(prefix = "fineract")
public class FineractProperties {

    private String baseUrl = "http://localhost:8444";
    private String tenant = "default";
    private String username = "mifos";
    private String password = "password";
    /** Page size for the paginated client listing — also lets tests exercise multi-page pagination with a small value. */
    private int clientPageSize = 200;

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

    public int getClientPageSize() {
        return clientPageSize;
    }

    public void setClientPageSize(int clientPageSize) {
        this.clientPageSize = clientPageSize;
    }
}
