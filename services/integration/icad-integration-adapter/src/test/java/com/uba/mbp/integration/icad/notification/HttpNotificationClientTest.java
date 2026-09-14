package com.uba.mbp.integration.icad.notification;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.uba.mbp.integration.icad.config.NotificationServiceProperties;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;

/**
 * Deliberately not a {@code @SpringBootTest} — same rationale as
 * {@code FineractHttpClientTest}: isolate exactly what's under test, the
 * real HTTP call to a WireMock-stubbed notification-service (ADR-0019).
 */
class HttpNotificationClientTest {

    private WireMockServer wireMock;
    private CamelContext camelContext;
    private HttpNotificationClient client;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(0);
        wireMock.start();

        camelContext = new DefaultCamelContext();
        camelContext.start();
        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();

        ObjectMapper objectMapper = JsonMapper.builder().build();

        NotificationServiceProperties properties = new NotificationServiceProperties();
        properties.setBaseUrl("http://localhost:" + wireMock.port());

        client = new HttpNotificationClient(producerTemplate, objectMapper, properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        camelContext.stop();
        wireMock.stop();
    }

    @Test
    void alertOperationsPostsToNotificationServiceWithTheOperationsGroup() {
        wireMock.stubFor(post(urlEqualTo("/notifications"))
                .willReturn(aResponse().withStatus(200)));

        client.alertOperations("fetchAccount failed", "boom");

        wireMock.verify(postRequestedFor(urlEqualTo("/notifications"))
                .withRequestBody(equalToJson("""
                        {"recipientGroup":"OPERATIONS","subject":"fetchAccount failed","body":"boom"}
                        """)));
    }

    @Test
    void aNotificationServiceOutageIsSwallowedNotRethrown() {
        wireMock.stop();

        client.alertOperations("fetchAccount failed", "boom");
        // No exception propagated — the alert falls back to the local log.
    }
}
