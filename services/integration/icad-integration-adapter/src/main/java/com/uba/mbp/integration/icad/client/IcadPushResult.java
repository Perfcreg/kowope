package com.uba.mbp.integration.icad.client;

/** ICAD's synchronous ack to a pushAccount call — a reference to poll, not a final outcome. */
public record IcadPushResult(String reference, IcadClearanceStatus status) {
}
