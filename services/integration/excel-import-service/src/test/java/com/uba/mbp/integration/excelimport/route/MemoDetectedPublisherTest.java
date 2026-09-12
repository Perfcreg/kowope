package com.uba.mbp.integration.excelimport.route;

import com.uba.mbp.integration.excelimport.event.MemoDetectedEvent;
import com.uba.mbp.integration.excelimport.validate.AcceptedRow;
import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deliberately NOT a {@code @SpringBootTest} against a shared Testcontainers
 * Kafka: this tests {@link MemoDetectedPublisher}'s own header-detection
 * logic in isolation, against a minimal test-only route that either succeeds
 * or fails fast — the real production route's happy path is already covered
 * end-to-end by {@code ExcelImportRouteTest} against a real broker.
 */
class MemoDetectedPublisherTest {

    private CamelContext camelContext;
    private ProducerTemplate producerTemplate;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();
        producerTemplate = camelContext.createProducerTemplate();
    }

    @AfterEach
    void tearDown() throws Exception {
        camelContext.stop();
    }

    private AcceptedRow sampleRow(int rowNumber, String accountNumber) {
        MemoDetectedEvent event = new MemoDetectedEvent(accountNumber, "CUST-1", "SOL-001", "NGN", "TXN-1",
                "written off", new BigDecimal("1000.00"), Instant.parse("2026-01-10T00:00:00Z").atZone(ZoneOffset.UTC).toInstant(),
                "excel-import-service", null);
        return new AcceptedRow(rowNumber, event);
    }

    @Test
    void reportsNoFailuresWhenEveryPublishSucceeds() throws Exception {
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:publishMemoDetected").routeId("publish-memo-detected").to("log:success");
            }
        });

        Set<Integer> failed = new MemoDetectedPublisher(producerTemplate)
                .publishAndReturnFailedRowNumbers(List.of(sampleRow(2, "ACC-001"), sampleRow(3, "ACC-002")));

        assertTrue(failed.isEmpty());
    }

    @Test
    void reportsARowAsFailedWhenItsPublishExhaustsRetries() throws Exception {
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:publishMemoDetected")
                        .routeId("publish-memo-detected")
                        .onException(Exception.class)
                                .maximumRedeliveries(1)
                                .redeliveryDelay(0)
                                .retryAttemptedLogLevel(LoggingLevel.OFF)
                                .handled(true)
                                .process(exchange -> exchange.getIn().setHeader(MemoDetectedPublisher.PUBLISH_FAILED_HEADER, true))
                        .end()
                        .process(exchange -> {
                            throw new RuntimeException("simulated permanent Kafka failure");
                        });
            }
        });

        Set<Integer> failed = new MemoDetectedPublisher(producerTemplate)
                .publishAndReturnFailedRowNumbers(List.of(sampleRow(2, "ACC-001"), sampleRow(3, "ACC-002")));

        assertEquals(Set.of(2, 3), failed);
    }

    @Test
    void onlyTheFailingRowIsReportedWhenOthersSucceed() throws Exception {
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:publishMemoDetected")
                        .routeId("publish-memo-detected")
                        .onException(Exception.class)
                                .maximumRedeliveries(0)
                                .handled(true)
                                .process(exchange -> exchange.getIn().setHeader(MemoDetectedPublisher.PUBLISH_FAILED_HEADER, true))
                        .end()
                        .process(exchange -> {
                            MemoDetectedEvent event = exchange.getIn().getBody(MemoDetectedEvent.class);
                            if ("ACC-BAD".equals(event.accountNumber())) {
                                throw new RuntimeException("simulated failure for this one account only");
                            }
                        });
            }
        });

        Set<Integer> failed = new MemoDetectedPublisher(producerTemplate)
                .publishAndReturnFailedRowNumbers(List.of(sampleRow(2, "ACC-001"), sampleRow(3, "ACC-BAD"), sampleRow(4, "ACC-003")));

        assertEquals(Set.of(3), failed);
    }

    @Test
    void twoRowsSharingTheSameAccountNumberAreCorrelatedIndependentlyByRow() throws Exception {
        // The regression this test guards against: correlating by account
        // number instead of row number would report BOTH rows as failed even
        // though only the second one's publish actually failed.
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:publishMemoDetected")
                        .routeId("publish-memo-detected")
                        .onException(Exception.class)
                                .maximumRedeliveries(0)
                                .handled(true)
                                .process(exchange -> exchange.getIn().setHeader(MemoDetectedPublisher.PUBLISH_FAILED_HEADER, true))
                        .end()
                        .process(exchange -> {
                            MemoDetectedEvent event = exchange.getIn().getBody(MemoDetectedEvent.class);
                            if ("TXN-1".equals(event.postingReference()) && event.narration().contains("second")) {
                                throw new RuntimeException("simulated failure for only the second transaction on this account");
                            }
                        });
            }
        });

        AcceptedRow firstTransaction = sampleRow(2, "ACC-SAME");
        MemoDetectedEvent secondEvent = new MemoDetectedEvent("ACC-SAME", "CUST-1", "SOL-001", "NGN", "TXN-1",
                "written off, second transaction", new BigDecimal("2000.00"), Instant.parse("2026-01-11T00:00:00Z"),
                "excel-import-service", null);
        AcceptedRow secondTransaction = new AcceptedRow(3, secondEvent);

        Set<Integer> failed = new MemoDetectedPublisher(producerTemplate)
                .publishAndReturnFailedRowNumbers(List.of(firstTransaction, secondTransaction));

        assertEquals(Set.of(3), failed);
    }
}
