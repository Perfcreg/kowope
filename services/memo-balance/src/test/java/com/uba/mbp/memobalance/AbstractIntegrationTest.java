package com.uba.mbp.memobalance;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers-backed Postgres + Kafka for every memo-balance integration
 * test (ADR-0009): tests run against real infra, never mocks, at the module's
 * public seams (REST API, Kafka consumer/producer boundary).
 */
@Testcontainers
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("mbp")
                    .withUsername("mbp")
                    .withPassword("mbp");

    // Uses the Confluent image Testcontainers' KafkaContainer is built and wait-strategy-tested
    // against, even though local dev (docker-compose.yml) runs apache/kafka directly — the two
    // are unrelated setups, and this one only needs to be reliable for tests.
    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
