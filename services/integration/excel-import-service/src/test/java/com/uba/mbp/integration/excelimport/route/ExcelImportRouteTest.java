package com.uba.mbp.integration.excelimport.route;

import com.uba.mbp.integration.excelimport.fixtures.ExcelFixtures;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
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

import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0011 end to end: real Camel route (Message Translator/Kafka Producer),
 * real Apache POI-generated .xlsx upload, real Testcontainers Kafka, real RBAC.
 * Per-event publish-failure detection/reconciliation is covered in isolation
 * by {@link MemoDetectedPublisherTest} and {@code ExcelImportProcessorTest}.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ExcelImportRouteTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;

    private Consumer<String, String> testConsumer;

    private RequestPostProcessor asMaximTeamUser(String subject) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(subject).claim("roles", List.of("MAXIM_TEAM")))
                .authorities(new SimpleGrantedAuthority("ROLE_MAXIM_TEAM"));
    }

    @BeforeEach
    void subscribeToMemoDetectedTopic() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        testConsumer = new KafkaConsumer<>(props);
        testConsumer.subscribe(List.of("mbp.integration.memo-detected"));
    }

    @AfterEach
    void closeConsumer() {
        testConsumer.close();
    }

    @Test
    void aValidUploadPublishesMemoDetectedForAcceptedRowsAndReportsRejectedOnes() throws Exception {
        InputStream workbook = ExcelFixtures.standardWorkbook(List.of(
                List.of("ACC-777", "CUST-9", "SOL-009", "NGN", "TXN-9", "written off", "125000.00", "2026-01-20", "NG"),
                Arrays.asList(null, "CUST-10", "SOL-010", "NGN", "TXN-10", "written off", "5000", "2026-01-21", "NG")));
        MockMultipartFile file = new MockMultipartFile(
                "file", "maxim-upload.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);

        mockMvc.perform(multipart("/excel-imports").file(file).with(asMaximTeamUser("maxim-user-9")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.rows[1].reason").value(org.hamcrest.Matchers.containsString("Account Number")));

        await().atMost(20, java.util.concurrent.TimeUnit.SECONDS).untilAsserted(() -> {
            var records = testConsumer.poll(Duration.ofSeconds(2));
            boolean found = false;
            for (ConsumerRecord<String, String> record : records) {
                if ("ACC-777".equals(record.key()) && record.value().contains("excel-import-service")) {
                    found = true;
                }
            }
            assertTrue(found, "Expected a MemoDetected message for account ACC-777");
        });
    }

    @Test
    void aRoleWithoutMaximTeamPrivilegeIsForbidden() throws Exception {
        InputStream workbook = ExcelFixtures.standardWorkbook(List.of());
        MockMultipartFile file = new MockMultipartFile(
                "file", "upload.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);

        RequestPostProcessor asCsm = SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.claim("roles", List.of("CSM")))
                .authorities(new SimpleGrantedAuthority("ROLE_CSM"));

        mockMvc.perform(multipart("/excel-imports").file(file).with(asCsm))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnauthenticatedUploadIsRejected() throws Exception {
        InputStream workbook = ExcelFixtures.standardWorkbook(List.of());
        MockMultipartFile file = new MockMultipartFile(
                "file", "upload.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);

        mockMvc.perform(multipart("/excel-imports").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aWorkbookMissingARequiredColumnIsRejectedAsBadRequest() throws Exception {
        InputStream workbook = ExcelFixtures.workbook(
                List.of("Account Number", "Customer ID"), List.of());
        MockMultipartFile file = new MockMultipartFile(
                "file", "incomplete.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);

        mockMvc.perform(multipart("/excel-imports").file(file).with(asMaximTeamUser("maxim-user-9")))
                .andExpect(status().isBadRequest());
    }
}
