package com.uba.mbp.integration.visionetl.fineract;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.uba.mbp.integration.visionetl.config.FineractProperties;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deliberately NOT a {@code @SpringBootTest}: a bare Camel context avoids the
 * shared, continuously-firing background timer a full Spring Boot context
 * would bring in (see write-off-detection-service's own FineractHttpClientTest
 * for why an earlier route-level version of this test was flaky).
 */
class FineractHttpClientTest {

    private WireMockServer wireMock;
    private CamelContext camelContext;
    private FineractHttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(0);
        wireMock.start();

        camelContext = new DefaultCamelContext();
        camelContext.start();
        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();

        FineractProperties properties = new FineractProperties();
        properties.setBaseUrl("http://localhost:" + wireMock.port());
        properties.setClientPageSize(2);

        ObjectMapper objectMapper = JsonMapper.builder().build();

        client = new FineractHttpClient(producerTemplate, properties, objectMapper);
    }

    @AfterEach
    void tearDown() throws Exception {
        camelContext.stop();
        wireMock.stop();
    }

    @Test
    void listActiveClientIdsFollowsPaginationAcrossMultiplePages() {
        wireMock.stubFor(get(urlPathEqualTo("/fineract-provider/api/v1/clients"))
                .withQueryParam("offset", equalTo("0"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"totalFilteredRecords":3,"pageItems":[{"id":1},{"id":2}]}
                        """)));
        wireMock.stubFor(get(urlPathEqualTo("/fineract-provider/api/v1/clients"))
                .withQueryParam("offset", equalTo("2"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"totalFilteredRecords":3,"pageItems":[{"id":3}]}
                        """)));

        List<Long> ids = client.listActiveClientIds();

        assertEquals(List.of(1L, 2L, 3L), ids);
    }

    @Test
    void listActiveClientIdsStopsAtASinglePageWhenThereIsNoMore() {
        wireMock.stubFor(get(urlPathEqualTo("/fineract-provider/api/v1/clients"))
                .withQueryParam("offset", equalTo("0"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"totalFilteredRecords":1,"pageItems":[{"id":4}]}
                        """)));

        List<Long> ids = client.listActiveClientIds();

        assertEquals(List.of(4L), ids);
        wireMock.verify(1, com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                urlPathEqualTo("/fineract-provider/api/v1/clients")));
    }

    @Test
    void listSavingsAccountBalancesReadsTheRealAccountBalanceField() {
        wireMock.stubFor(get(urlPathEqualTo("/fineract-provider/api/v1/clients/4/accounts"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"savingsAccounts":[{"accountNo":"000000004","accountBalance":6125000.00}]}
                        """)));

        List<FineractAccountBalance> balances = client.listSavingsAccountBalances(4L);

        assertEquals(1, balances.size());
        assertEquals("000000004", balances.get(0).accountNo());
        assertTrue(new java.math.BigDecimal("6125000.00").compareTo(balances.get(0).accountBalance()) == 0);
    }
}
