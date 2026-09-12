package com.uba.mbp.integration.writeoffdetection.fineract;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.uba.mbp.integration.writeoffdetection.config.FineractProperties;
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
 * would bring in (see {@code WriteOffDetectionRoute}'s 1s poll), which made
 * an earlier version of this pagination test flaky/order-dependent. This
 * isolates exactly what's under test — {@link FineractHttpClient}'s HTTP
 * calls against a real WireMock-stubbed Fineract, per ADR-0011/0012.
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
    void listActiveClientsFollowsPaginationAcrossMultiplePages() {
        wireMock.stubFor(get(urlPathEqualTo("/fineract-provider/api/v1/clients"))
                .withQueryParam("offset", equalTo("0"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"totalFilteredRecords":3,"pageItems":[
                            {"id":1,"displayName":"Client One","officeName":"Lagos Island Branch"},
                            {"id":2,"displayName":"Client Two","officeName":"Lagos Island Branch"}
                        ]}
                        """)));
        wireMock.stubFor(get(urlPathEqualTo("/fineract-provider/api/v1/clients"))
                .withQueryParam("offset", equalTo("2"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"totalFilteredRecords":3,"pageItems":[
                            {"id":3,"displayName":"Client Three","officeName":"Abuja Branch"}
                        ]}
                        """)));

        List<FineractCustomerSummary> clients = client.listActiveClients();

        assertEquals(3, clients.size());
        assertTrue(clients.stream().anyMatch(c -> c.id() == 1L));
        assertTrue(clients.stream().anyMatch(c -> c.id() == 2L));
        assertTrue(clients.stream().anyMatch(c -> c.id() == 3L));
    }

    @Test
    void listActiveClientsStopsAtASinglePageWhenThereIsNoMore() {
        wireMock.stubFor(get(urlPathEqualTo("/fineract-provider/api/v1/clients"))
                .withQueryParam("offset", equalTo("0"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"totalFilteredRecords":1,"pageItems":[{"id":4,"displayName":"Emeka Nwosu","officeName":"Abuja Branch"}]}
                        """)));

        List<FineractCustomerSummary> clients = client.listActiveClients();

        assertEquals(1, clients.size());
        assertEquals(4L, clients.get(0).id());
        // Only one request should have been made — a second page was never fetched.
        wireMock.verify(1, com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                urlPathEqualTo("/fineract-provider/api/v1/clients")));
    }

    @Test
    void getSavingsAccountWithTransactionsReadsTheAccountBalanceFromSummaryNotFromAnyTransaction() {
        wireMock.stubFor(get(urlPathEqualTo("/fineract-provider/api/v1/savingsaccounts/10"))
                .withQueryParam("associations", equalTo("transactions"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"accountNo":"000000004","currency":{"code":"NGN"},
                         "summary":{"accountBalance":6125000.00},
                         "transactions":[{"id":98,"note":"Account written off per approval","amount":15000.00,"date":[2026,1,10]}]}
                        """)));

        FineractSavingsAccount account = client.getSavingsAccountWithTransactions(10L);

        assertEquals(0, new java.math.BigDecimal("6125000.00").compareTo(account.accountBalance()));
        assertEquals(1, account.transactions().size());
        assertEquals("Account written off per approval", account.transactions().get(0).note());
    }
}
