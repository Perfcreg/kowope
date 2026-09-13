package com.uba.mbp.sharedplatform.auth.web;

public record AccessTokenResponse(String accessToken, String tokenType) {
    public static AccessTokenResponse of(String accessToken) {
        return new AccessTokenResponse(accessToken, "Bearer");
    }
}
