package com.uba.mbp.sharedplatform.notification.event;

import java.time.Instant;

/**
 * This service's own copy of {@code memo-balance}'s published event — see
 * services/memo-balance/CONTEXT.md#published-events for the authoritative
 * shape. Each consumer owns its own copy, per ADR-0001's event-driven (not
 * shared-code) coupling — the same convention every `integration` adapter
 * already follows for its own consumed/published events.
 */
public record MemoLiquidatedEvent(String accountNumber, Instant liquidatedAt) {
}
