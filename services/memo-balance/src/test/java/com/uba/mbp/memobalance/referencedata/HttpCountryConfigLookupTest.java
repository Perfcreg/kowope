package com.uba.mbp.memobalance.referencedata;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Deliberately not a {@code @SpringBootTest} — isolates exactly what's under
 * test, the real HTTP call to a WireMock-stubbed reference-data-config
 * (ADR-0021), same rationale as this repo's other {@code Http*ClientTest}s.
 */
class HttpCountryConfigLookupTest {

    private WireMockServer wireMock;
    private HttpCountryConfigLookup lookup;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(0);
        wireMock.start();
        lookup = new HttpCountryConfigLookup("http://localhost:" + wireMock.port());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void resolvesARealCountryOverHttp() {
        wireMock.stubFor(get(urlEqualTo("/countries/GH"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"countryCode":"GH","region":"Africa & Nigeria","baseCurrency":"GHS","glWriteOffCode":"GL-WO-GH","glRecoveryCode":"GL-REC-GH"}
                        """)));

        CountryConfig config = lookup.lookup("GH");

        assertEquals("GH", config.countryCode());
        assertEquals("GHS", config.baseCurrency());
        assertEquals("GL-WO-GH", config.glWriteOffCode());
        assertEquals("GL-REC-GH", config.glRecoveryCode());
    }

    @Test
    void fallsBackToTheNgDefaultForAnUnknownCountry() {
        wireMock.stubFor(get(urlEqualTo("/countries/ZZ"))
                .willReturn(aResponse().withStatus(404)));

        CountryConfig config = lookup.lookup("ZZ");

        assertEquals("NGN", config.baseCurrency());
    }

    @Test
    void fallsBackToTheNgDefaultForANullCountryInsteadOfThrowing() {
        CountryConfig config = lookup.lookup(null);

        assertEquals("NGN", config.baseCurrency());
    }

    @Test
    void fallsBackToTheNgDefaultWhenReferenceDataConfigIsUnreachable() {
        wireMock.stop();

        CountryConfig config = lookup.lookup("NG");

        assertEquals("NGN", config.baseCurrency());
    }
}
