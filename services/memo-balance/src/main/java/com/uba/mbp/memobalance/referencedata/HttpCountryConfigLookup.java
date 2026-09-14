package com.uba.mbp.memobalance.referencedata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The real seam onto reference-data-config (RFP §3.14), replacing
 * {@code StaticCountryConfigLookup} now that it exists. A null/unrecognized/
 * unreachable country falls back to the same NG default the old stand-in
 * always returned — ingestion (RFP §3.13(bis)) must never fail just because
 * Country resolution did.
 */
@Component
public class HttpCountryConfigLookup implements CountryConfigLookup {

    private static final Logger log = LoggerFactory.getLogger(HttpCountryConfigLookup.class);
    private static final CountryConfig DEFAULT = new CountryConfig("NG", "NGN", "GL-WRITEOFF-NG", "GL-RECOVERY-NG");

    private final RestClient restClient;

    public HttpCountryConfigLookup(@Value("${reference-data-config.base-url}") String baseUrl) {
        // Built via the static factory, not an injected RestClient.Builder
        // bean — this repo's Boot setup doesn't autoconfigure one (no
        // RestClientAutoConfiguration-triggering HTTP client library beyond
        // the JDK default), and RestClient.builder() needs no Spring context
        // dependency anyway.
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public CountryConfig lookup(String countryCode) {
        if (countryCode == null) {
            return DEFAULT;
        }
        try {
            CountryConfigResponse response = restClient.get()
                    .uri("/countries/{code}", countryCode)
                    .retrieve()
                    .body(CountryConfigResponse.class);
            if (response == null) {
                return DEFAULT;
            }
            return new CountryConfig(response.countryCode(), response.baseCurrency(),
                    response.glWriteOffCode(), response.glRecoveryCode());
        } catch (RestClientException e) {
            // Covers both a 404 (unrecognized country) and reference-data-config
            // being unreachable — ingestion degrades to the NG default rather
            // than failing, the same fallback StaticCountryConfigLookup gave
            // for an unconfigured code.
            log.warn("Falling back to default Country config for '{}': {}", countryCode, e.getMessage());
            return DEFAULT;
        }
    }

    private record CountryConfigResponse(String countryCode, String region, String baseCurrency,
                                          String glWriteOffCode, String glRecoveryCode) {
    }
}
