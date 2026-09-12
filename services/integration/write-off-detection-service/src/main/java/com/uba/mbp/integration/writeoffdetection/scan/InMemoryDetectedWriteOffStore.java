package com.uba.mbp.integration.writeoffdetection.scan;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryDetectedWriteOffStore implements DetectedWriteOffStore {

    private final Set<String> detected = ConcurrentHashMap.newKeySet();

    @Override
    public boolean alreadyDetected(long savingsAccountId, long transactionId) {
        return detected.contains(key(savingsAccountId, transactionId));
    }

    @Override
    public void markDetected(long savingsAccountId, long transactionId) {
        detected.add(key(savingsAccountId, transactionId));
    }

    private String key(long savingsAccountId, long transactionId) {
        return savingsAccountId + ":" + transactionId;
    }
}
