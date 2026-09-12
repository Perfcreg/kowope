package com.uba.mbp.memobalance.messaging;

import com.uba.mbp.memobalance.event.MemoDetectedEvent;
import com.uba.mbp.memobalance.exception.InvalidMemoDetectedEventException;
import com.uba.mbp.memobalance.service.MemoIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MemoDetectedListener {

    private static final Logger log = LoggerFactory.getLogger(MemoDetectedListener.class);

    private final MemoIngestionService ingestionService;

    public MemoDetectedListener(MemoIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @KafkaListener(
            topics = MemoTopics.MEMO_DETECTED,
            groupId = "memo-balance",
            containerFactory = "memoDetectedContainerFactory")
    public void onMemoDetected(MemoDetectedEvent event) {
        try {
            ingestionService.ingest(event);
        } catch (InvalidMemoDetectedEventException e) {
            // Already audited by the service. Don't rethrow: a malformed message
            // must not block the partition for every message behind it.
            log.warn("Rejected an invalid MemoDetected event: {}", e.getMessage());
        }
    }
}
