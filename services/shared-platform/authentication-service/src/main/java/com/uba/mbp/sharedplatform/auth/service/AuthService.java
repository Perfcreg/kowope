package com.uba.mbp.sharedplatform.auth.service;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.sharedplatform.auth.domain.User;
import com.uba.mbp.sharedplatform.auth.domain.UserRepository;
import com.uba.mbp.sharedplatform.auth.mfa.TotpService;
import com.uba.mbp.sharedplatform.auth.token.PendingLoginStore;
import com.uba.mbp.sharedplatform.auth.token.RefreshTokenStore;
import com.uba.mbp.sharedplatform.auth.token.TokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * The login → MFA → token flow (RFP §3.7 User Stories 1, 2, 3, 6). Plain,
 * Spring-independent orchestration logic over injected collaborators — the
 * same "business logic in beans, Spring only wires it" discipline used
 * throughout {@code integration}.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final PendingLoginStore pendingLoginStore;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenService tokenService;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TotpService totpService,
            PendingLoginStore pendingLoginStore,
            RefreshTokenStore refreshTokenStore,
            TokenService tokenService,
            AuditLogger auditLogger,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
        this.pendingLoginStore = pendingLoginStore;
        this.refreshTokenStore = refreshTokenStore;
        this.tokenService = tokenService;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    /** Verifies the password and, if correct, returns a pending-login handle awaiting MFA. Never issues a token by itself — MFA is mandatory. */
    public String login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            audit("LOGIN_FAILED", username, "Bad username or password");
            throw new AuthenticationFailedException("Invalid username or password");
        }
        audit("LOGIN_PASSWORD_VERIFIED", username, "Password verified; awaiting MFA");
        return pendingLoginStore.issue(username);
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }

    /**
     * Completes login: verifies the TOTP code for the pending handle and issues a real
     * access + refresh token pair.
     *
     * <p>Deliberate design: the pending-login handle is consumed (deleted) on this call
     * whether or not the code turns out valid — a wrong code doesn't leave the handle
     * usable for another guess. This caps server-side code-guessing at one attempt per
     * password verification (a real anti-bruteforce property), at the cost of a fat-
     * fingered digit forcing a fresh {@link #login} rather than a same-handle retry.
     */
    public TokenPair verifyMfa(String pendingLoginId, String code) {
        String username = pendingLoginStore.consume(pendingLoginId)
                .orElseThrow(() -> new AuthenticationFailedException("Login session expired or already used"));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationFailedException("User no longer exists"));

        if (!totpService.isValidCode(user.getMfaSecret(), code)) {
            audit("MFA_FAILED", username, "Invalid TOTP code");
            throw new AuthenticationFailedException("Invalid MFA code");
        }

        audit("LOGIN_SUCCEEDED", username, "MFA verified");
        String accessToken = tokenService.issueAccessToken(username, user.getRoles());
        String refreshToken = refreshTokenStore.issue(username);
        return new TokenPair(accessToken, refreshToken);
    }

    /** Issues a fresh access token for a still-active session, sliding the inactivity-timeout window (RFP §4.3). */
    public String refresh(String refreshToken) {
        String username = refreshTokenStore.resolveAndSlide(refreshToken)
                .orElseThrow(() -> new AuthenticationFailedException("Session expired or refresh token invalid"));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationFailedException("User no longer exists"));
        return tokenService.issueAccessToken(username, user.getRoles());
    }

    /** Issues a short-lived "sensitive" token (RFP §4.3): requires a still-active session AND a fresh TOTP code. */
    public String stepUp(String refreshToken, String code) {
        String username = refreshTokenStore.resolve(refreshToken)
                .orElseThrow(() -> new AuthenticationFailedException("Session expired or refresh token invalid"));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationFailedException("User no longer exists"));

        if (!totpService.isValidCode(user.getMfaSecret(), code)) {
            audit("STEP_UP_FAILED", username, "Invalid TOTP code");
            throw new AuthenticationFailedException("Invalid MFA code");
        }

        audit("STEP_UP_SUCCEEDED", username, "Sensitive-action token issued");
        return tokenService.issueStepUpToken(username, user.getRoles());
    }

    private void audit(String action, String username, String detail) {
        auditLogger.record(new AuditEvent(
                clock.instant(), username, action, "User", username, "authentication-service", detail));
    }
}
