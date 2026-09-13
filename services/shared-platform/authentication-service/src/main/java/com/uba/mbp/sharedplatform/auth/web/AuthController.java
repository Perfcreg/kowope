package com.uba.mbp.sharedplatform.auth.web;

import com.uba.mbp.sharedplatform.auth.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The login → MFA → token REST contract (RFP §3.7, §4.3 User Stories 1, 2, 3, 6).
 * Every endpoint here is deliberately unauthenticated (see SecurityConfig) —
 * this is how a caller obtains a token in the first place.
 */
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public LoginPendingResponse login(@RequestBody LoginRequest request) {
        String pendingLoginId = authService.login(request.username(), request.password());
        return LoginPendingResponse.of(pendingLoginId);
    }

    @PostMapping("/auth/mfa/verify")
    public TokenResponse verifyMfa(@RequestBody MfaVerifyRequest request) {
        AuthService.TokenPair tokens = authService.verifyMfa(request.pendingLoginId(), request.code());
        return TokenResponse.of(tokens.accessToken(), tokens.refreshToken());
    }

    @PostMapping("/auth/refresh")
    public AccessTokenResponse refresh(@RequestBody RefreshRequest request) {
        return AccessTokenResponse.of(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/auth/step-up")
    public AccessTokenResponse stepUp(@RequestBody StepUpRequest request) {
        return AccessTokenResponse.of(authService.stepUp(request.refreshToken(), request.code()));
    }
}
