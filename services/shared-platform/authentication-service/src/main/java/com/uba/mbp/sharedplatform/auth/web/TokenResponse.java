package com.uba.mbp.sharedplatform.auth.web;

public record TokenResponse(String accessToken, String refreshToken, String tokenType) {
    public static TokenResponse of(String accessToken, String refreshToken) {
        return new TokenResponse(accessToken, refreshToken, "Bearer");
    }
}
