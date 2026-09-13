package com.uba.mbp.sharedplatform.auth.web;

/** Password verified; MFA still required before a token is issued (RFP §4.3 — MFA is mandatory, no bypass). */
public record LoginPendingResponse(String pendingLoginId, boolean mfaRequired) {
    public static LoginPendingResponse of(String pendingLoginId) {
        return new LoginPendingResponse(pendingLoginId, true);
    }
}
