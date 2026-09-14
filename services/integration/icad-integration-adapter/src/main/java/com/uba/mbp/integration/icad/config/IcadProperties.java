package com.uba.mbp.integration.icad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * ADR-0014: no real NIBSS ICAD sandbox is reachable from this project — every
 * value here points at a WireMock stub of the pushAccount/fetchAccount
 * contract this adapter models, not a real ICAD environment.
 */
@ConfigurationProperties(prefix = "icad")
public class IcadProperties {

    private String baseUrl = "http://localhost:9999";
    private String apiKey = "dev-only-placeholder";
    /** RFP §3.8/§4.6's stated window is 24-48 hours; ADR-0020 escalates at the outer edge. */
    private Duration escalationSla = Duration.ofHours(48);

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

    public Duration getEscalationSla() {
        return escalationSla;
    }

    public void setEscalationSla(Duration escalationSla) {
        this.escalationSla = escalationSla;
    }
}
