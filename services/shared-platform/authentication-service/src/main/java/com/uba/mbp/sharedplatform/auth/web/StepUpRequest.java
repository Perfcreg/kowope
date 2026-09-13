package com.uba.mbp.sharedplatform.auth.web;

public record StepUpRequest(String refreshToken, String code) {
}
