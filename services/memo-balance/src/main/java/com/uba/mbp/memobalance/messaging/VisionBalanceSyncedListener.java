package com.uba.mbp.memobalance.messaging;

import com.uba.mbp.memobalance.event.VisionBalanceSyncedEvent;
import com.uba.mbp.memobalance.repository.MemoAccountRepository;
import com.uba.mbp.memobalance.service.MemoExceptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Ticket 06: reconciles memo-balance's own figure against Vision's reported balance. */
@Component
public class VisionBalanceSyncedListener {

    private static final Logger log = LoggerFactory.getLogger(VisionBalanceSyncedListener.class);

    private final MemoAccountRepository memoAccountRepository;
    private final MemoExceptionService exceptionService;

    public VisionBalanceSyncedListener(
            MemoAccountRepository memoAccountRepository, MemoExceptionService exceptionService) {
        this.memoAccountRepository = memoAccountRepository;
        this.exceptionService = exceptionService;
    }

    @KafkaListener(
            topics = MemoTopics.VISION_BALANCE_SYNCED,
            groupId = "memo-balance",
            containerFactory = "visionBalanceSyncedContainerFactory")
    public void onVisionBalanceSynced(VisionBalanceSyncedEvent event) {
        memoAccountRepository.findByAccountNumber(event.accountNumber())
                .ifPresentOrElse(
                        account -> exceptionService.checkForVisionDiscrepancy(account, event.balance()),
                        () -> log.info(
                                "Vision reported a balance for an account with no Memo record ({}); ignoring.",
                                event.accountNumber()));
    }
}
