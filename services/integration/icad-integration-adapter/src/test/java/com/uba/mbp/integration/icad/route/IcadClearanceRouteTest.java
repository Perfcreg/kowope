package com.uba.mbp.integration.icad.route;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0011/0014 end to end: real Camel routes (synchronous push + polling
 * consumer), real RBAC, real Testcontainers Kafka — but ICAD itself is
 * WireMock, since no real sandbox exists to verify against (unlike Fineract
 * for Finacle/Vision).
 */
@Testcontainers
@SpringBootTest(properties = {"icad.poll.interval-ms=1000"})
@AutoConfigureMockMvc
class IcadClearanceRouteTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    static WireMockServer wireMock;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        wireMock = new WireMockServer(0);
        wireMock.start();
        registry.add("icad.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    private Consumer<String, String> testConsumer;

    private RequestPostProcessor asCreditAdmin() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject("credit-admin-1").claim("roles", List.of("CREDIT_ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_CREDIT_ADMIN"));
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        testConsumer = new KafkaConsumer<>(props);
        testConsumer.subscribe(List.of("mbp.integration.icad-clearance-outcome"));
    }

    @AfterEach
    void closeConsumer() {
        testConsumer.close();
    }

    @Test
    void aClearanceRequestIsPushedAndLaterResolvedOnKafkaOnceIcadClears() throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/icad/v1/accounts"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"reference":"REF-777","status":"PENDING"}
                        """)));
        wireMock.stubFor(get(urlPathEqualTo("/icad/v1/accounts/REF-777"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"reference":"REF-777","status":"CLEARED"}
                        """)));

        String body = """
                {"accountNumber":"ACC-777","customerId":"CUST-9","customerName":"Jane Doe","bvn":"12345678901","clearedDate":"2026-01-10T00:00:00Z"}
                """;

        mockMvc.perform(post("/icad/clearance-requests").with(asCreditAdmin())
                        .contentType("application/json").content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accountNumber").value("ACC-777"))
                .andExpect(jsonPath("$.icadReference").value("REF-777"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        await().atMost(20, java.util.concurrent.TimeUnit.SECONDS).untilAsserted(() -> {
            var records = testConsumer.poll(Duration.ofSeconds(2));
            boolean found = false;
            for (ConsumerRecord<String, String> record : records) {
                if ("ACC-777".equals(record.key()) && record.value().contains("CLEARED")) {
                    found = true;
                }
            }
            assertTrue(found, "Expected an IcadClearanceOutcome message for account ACC-777");
        });
    }

    @Test
    void aResolvedClearanceIsNotRePublishedOnALaterPollCycle() throws Exception {
        // ADR-0015: removal only happens after a confirmed Kafka publish —
        // this proves the whole push -> resolve -> publish -> remove chain
        // actually completes, not just that the first publish happens.
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/icad/v1/accounts"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"reference":"REF-888","status":"PENDING"}
                        """)));
        wireMock.stubFor(get(urlPathEqualTo("/icad/v1/accounts/REF-888"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"reference":"REF-888","status":"CLEARED"}
                        """)));

        String body = """
                {"accountNumber":"ACC-888","customerId":"CUST-8","customerName":"John Doe","bvn":"98765432109","clearedDate":"2026-01-10T00:00:00Z"}
                """;
        mockMvc.perform(post("/icad/clearance-requests").with(asCreditAdmin())
                        .contentType("application/json").content(body))
                .andExpect(status().isAccepted());

        await().atMost(20, java.util.concurrent.TimeUnit.SECONDS).untilAsserted(() -> {
            var records = testConsumer.poll(Duration.ofSeconds(2));
            boolean found = false;
            for (ConsumerRecord<String, String> record : records) {
                if ("ACC-888".equals(record.key())) {
                    found = true;
                }
            }
            assertTrue(found, "Expected the first outcome message for account ACC-888");
        });

        // Give at least one more poll cycle (interval-ms=1000) a chance to run.
        Thread.sleep(3000);
        var laterRecords = testConsumer.poll(Duration.ofSeconds(2));
        long acc888Count = 0;
        for (ConsumerRecord<String, String> record : laterRecords) {
            if ("ACC-888".equals(record.key())) {
                acc888Count++;
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(0, acc888Count,
                "A resolved clearance must not be re-published on a later poll cycle");
    }

    @Test
    void aRoleWithoutClearancePrivilegeIsForbidden() throws Exception {
        RequestPostProcessor asCsm = SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.claim("roles", List.of("CSM")))
                .authorities(new SimpleGrantedAuthority("ROLE_CSM"));

        mockMvc.perform(post("/icad/clearance-requests").with(asCsm)
                        .contentType("application/json")
                        .content("{\"accountNumber\":\"ACC-1\",\"customerId\":\"C-1\",\"customerName\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(post("/icad/clearance-requests")
                        .contentType("application/json")
                        .content("{\"accountNumber\":\"ACC-1\",\"customerId\":\"C-1\",\"customerName\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }
}
