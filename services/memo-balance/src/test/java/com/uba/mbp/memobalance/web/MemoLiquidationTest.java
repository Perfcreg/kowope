package com.uba.mbp.memobalance.web;

import com.uba.mbp.memobalance.AbstractIntegrationTest;
import com.uba.mbp.memobalance.domain.AdjustmentType;
import com.uba.mbp.memobalance.domain.MemoAccount;
import com.uba.mbp.memobalance.domain.MemoStatus;
import com.uba.mbp.memobalance.messaging.MemoTopics;
import com.uba.mbp.memobalance.repository.MemoAccountRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 05: publish MemoLiquidated the instant a memo balance reaches exactly zero.
 * Seam under test: the Kafka producer boundary, alongside the REST command that triggers it.
 */
@AutoConfigureMockMvc
class MemoLiquidationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemoAccountRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Consumer<String, String> testConsumer;

    private RequestPostProcessor asTransactionServices() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.claim("roles", List.of("TRANSACTION_SERVICES")))
                .authorities(new SimpleGrantedAuthority("ROLE_TRANSACTION_SERVICES"));
    }

    @BeforeEach
    void subscribeToLiquidatedTopic() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        testConsumer = new KafkaConsumer<>(props);
        testConsumer.subscribe(List.of(MemoTopics.MEMO_LIQUIDATED));
    }

    @AfterEach
    void closeConsumer() {
        testConsumer.close();
    }

    private void seedAccount(String accountNumber, BigDecimal balance) {
        MemoAccount account = MemoAccount.newlyDetected(
                accountNumber, "CUST-1", "SOL-001", "NGN", "TXN-REF-1",
                "written off", balance, Instant.parse("2026-01-01T00:00:00Z"), Instant.now());
        repository.save(account);
    }

    private void submitAdjustment(String accountNumber, BigDecimal amount) throws Exception {
        String body = objectMapper.writeValueAsString(
                new AdjustmentRequest(AdjustmentType.PARTIAL_PAYMENT, amount));
        mockMvc.perform(post("/memo-accounts/{accountNumber}/adjustments", accountNumber)
                        .with(asTransactionServices())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNoContent());
    }

    @Test
    void anAdjustmentThatZeroesTheBalancePublishesMemoLiquidated() throws Exception {
        String accountNumber = "ACC-" + System.nanoTime();
        seedAccount(accountNumber, new BigDecimal("500.00"));

        submitAdjustment(accountNumber, new BigDecimal("500.00"));

        await().atMost(15, java.util.concurrent.TimeUnit.SECONDS).untilAsserted(() -> {
            MemoAccount updated = repository.findByAccountNumber(accountNumber).orElseThrow();
            assertEquals(MemoStatus.LIQUIDATED, updated.getStatus());
        });

        var records = testConsumer.poll(Duration.ofSeconds(10));
        assertTrue(recordsContainAccount(records, accountNumber));
    }

    @Test
    void aPartialAdjustmentThatDoesNotZeroTheBalanceDoesNotPublishMemoLiquidated() throws Exception {
        String accountNumber = "ACC-" + System.nanoTime();
        seedAccount(accountNumber, new BigDecimal("500.00"));

        submitAdjustment(accountNumber, new BigDecimal("200.00"));

        await().atMost(15, java.util.concurrent.TimeUnit.SECONDS).untilAsserted(() -> {
            MemoAccount updated = repository.findByAccountNumber(accountNumber).orElseThrow();
            assertEquals(0, new BigDecimal("300.00").compareTo(updated.getBalance()));
            assertEquals(MemoStatus.IMPORTED_PENDING_REVIEW, updated.getStatus());
        });

        var records = testConsumer.poll(Duration.ofSeconds(5));
        assertTrue(!recordsContainAccount(records, accountNumber));
    }

    private boolean recordsContainAccount(ConsumerRecords<String, String> records, String accountNumber) {
        for (var record : records) {
            if (accountNumber.equals(record.key())) {
                return true;
            }
        }
        return false;
    }
}
