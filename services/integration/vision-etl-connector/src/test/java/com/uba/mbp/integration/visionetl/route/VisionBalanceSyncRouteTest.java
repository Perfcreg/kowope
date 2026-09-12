package com.uba.mbp.integration.visionetl.route;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

/** ADR-0011/0013 end to end: real Camel route, WireMock-stubbed Fineract, real Testcontainers Kafka. */
@Testcontainers
@SpringBootTest(properties = {"vision.sync.interval-ms=1000"})
class VisionBalanceSyncRouteTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    static WireMockServer wireMock;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        wireMock = new WireMockServer(0);
        wireMock.start();
        registry.add("fineract.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    private Consumer<String, String> testConsumer;

    @BeforeEach
    void subscribeToVisionBalanceSyncedTopic() {
        wireMock.resetAll();
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        testConsumer = new KafkaConsumer<>(props);
        testConsumer.subscribe(List.of("mbp.integration.vision-balance-synced"));
    }

    @AfterEach
    void closeConsumer() {
        testConsumer.close();
    }

    @Test
    void aFineractAccountBalanceProducesAVisionBalanceSyncedMessageOnKafka() {
        wireMock.stubFor(get(urlPathEqualTo("/fineract-provider/api/v1/clients"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"pageItems":[{"id":4}]}
                        """)));

        wireMock.stubFor(get(urlPathEqualTo("/fineract-provider/api/v1/clients/4/accounts"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"savingsAccounts":[{"accountNo":"000000004","accountBalance":6125000.00}]}
                        """)));

        await().atMost(20, java.util.concurrent.TimeUnit.SECONDS).untilAsserted(() -> {
            var records = testConsumer.poll(Duration.ofSeconds(2));
            boolean found = false;
            for (ConsumerRecord<String, String> record : records) {
                if ("000000004".equals(record.key()) && record.value().contains("6125000")) {
                    found = true;
                }
            }
            assertTrue(found, "Expected a VisionBalanceSynced message for account 000000004");
        });
    }
}
