package com.uba.mbp.memobalance.web;

import tools.jackson.databind.ObjectMapper;
import com.uba.mbp.memobalance.AbstractIntegrationTest;
import com.uba.mbp.memobalance.domain.AdjustmentType;
import com.uba.mbp.memobalance.domain.MemoAccount;
import com.uba.mbp.memobalance.messaging.MemoTopics;
import com.uba.mbp.memobalance.repository.MemoAccountRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 04: recalculate and adjust memo balance on partial payment or write-off;
 * publish MemoBalanceAdjusted. Seams under test: the REST command endpoint and
 * the Kafka producer boundary (ADR-0009).
 */
@AutoConfigureMockMvc
class MemoAdjustmentControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemoAccountRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Consumer<String, String> testConsumer;

    private RequestPostProcessor withRole(String role) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @BeforeEach
    void subscribeToAdjustedTopic() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        testConsumer = new KafkaConsumer<>(props);
        testConsumer.subscribe(List.of(MemoTopics.MEMO_BALANCE_ADJUSTED));
    }

    @AfterEach
    void closeConsumer() {
        testConsumer.close();
    }

    private MemoAccount seedAccount(String accountNumber, BigDecimal balance) {
        MemoAccount account = MemoAccount.newlyDetected(
                accountNumber, "CUST-1", "SOL-001", "NGN", "TXN-REF-1",
                "written off", balance, Instant.parse("2026-01-01T00:00:00Z"), Instant.now());
        return repository.save(account);
    }

    @Test
    void aPartialPaymentReducesTheBalanceAndPublishesTheAdjustment() throws Exception {
        String accountNumber = "ACC-" + System.nanoTime();
        seedAccount(accountNumber, new BigDecimal("1000.00"));

        String body = objectMapper.writeValueAsString(
                new AdjustmentRequest(AdjustmentType.PARTIAL_PAYMENT, new BigDecimal("400.00")));

        mockMvc.perform(post("/memo-accounts/{accountNumber}/adjustments", accountNumber)
                        .with(withRole("TRANSACTION_SERVICES"))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNoContent());

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            MemoAccount updated = repository.findByAccountNumber(accountNumber).orElseThrow();
            assertEquals(0, new BigDecimal("600.00").compareTo(updated.getBalance()));
        });

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(testConsumer, MemoTopics.MEMO_BALANCE_ADJUSTED, Duration.ofSeconds(15));
        assertTrue(record.value().contains("600"));
    }

    @Test
    void anAdjustmentExceedingTheBalanceIsRejected() throws Exception {
        String accountNumber = "ACC-" + System.nanoTime();
        seedAccount(accountNumber, new BigDecimal("100.00"));

        String body = objectMapper.writeValueAsString(
                new AdjustmentRequest(AdjustmentType.PARTIAL_PAYMENT, new BigDecimal("500.00")));

        mockMvc.perform(post("/memo-accounts/{accountNumber}/adjustments", accountNumber)
                        .with(withRole("CREDIT_ADMIN"))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aRoleWithoutAdjustPrivilegeIsForbidden() throws Exception {
        String accountNumber = "ACC-" + System.nanoTime();
        seedAccount(accountNumber, new BigDecimal("100.00"));

        String body = objectMapper.writeValueAsString(
                new AdjustmentRequest(AdjustmentType.PARTIAL_PAYMENT, new BigDecimal("10.00")));

        mockMvc.perform(post("/memo-accounts/{accountNumber}/adjustments", accountNumber)
                        .with(withRole("CSM"))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
