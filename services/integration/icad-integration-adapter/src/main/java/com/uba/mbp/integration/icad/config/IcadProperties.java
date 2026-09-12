package com.uba.mbp.integration.icad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ADR-0014: no real NIBSS ICAD sandbox is reachable from this project — every
 * value here points at a WireMock stub of the pushAccount/fetchAccount
 * contract this adapter models, not a real ICAD environment.
 */
@ConfigurationProperties(prefix = "icad")
public class IcadProperties {

    private String baseUrl = "http://localhost:9999";
    private String apiKey = "dev-only-placeholder";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
