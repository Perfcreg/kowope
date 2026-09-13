package com.uba.mbp.sharedplatform.auth.web;

public record MfaVerifyRequest(String pendingLoginId, String code) {
}
