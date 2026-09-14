package com.uba.mbp.integration.icad.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * OAuth2 resource-server security, RBAC-enforced via the {@code roles} JWT
 * claim (see {@link JwtRoleConverter}). Only Credit Admin / Recovery Team have
 * "full privileges — verification, liquidation tracking, reporting" (RFP
 * §3.7) — the two roles that would trigger an ICAD clearance request.
 *
 * <p>Enterprise-review gap (2026-09-12), still NOT fixed here: a real
 * {@code amr}/{@code session_class} claim convention now exists
 * (authentication-service's {@code TokenService}, ADR-0017), but this route
 * doesn't check it yet — a standard-session token still authorizes pushing a
 * clearance to ICAD, an irreversible external-system mutation, with no
 * enforcement that the caller recently re-verified MFA for this specific
 * action. Requiring {@code session_class: "sensitive"} here is real future
 * work this ADR unblocks, not something this ticket does — same deferred-gap
 * pattern as memo-balance's Country-scoping.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Stateless bearer-token API — CSRF is for cookie/session auth and
        // would otherwise turn an unauthenticated POST's 401 into a 403.
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer((OAuth2ResourceServerConfigurer<HttpSecurity> oauth2) -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(new JwtRoleConverter())));
        return http.build();
    }

    /**
     * System-wide-audit fix (2026-09-15): {@code JwtValidators.createDefault()}'s
     * timestamp check treats a missing {@code exp} claim as valid — a token
     * minted without one would never expire on this endpoint (an irreversible
     * external-system mutation, RFP §4.3's session-timeout intent). Same fix
     * already applied in excel-import-service's and reference-data-config's
     * copies; this one was missed when the pattern was established.
     */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${security.jwt.jwk-set-uri}") String jwkSetUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> requireExpiry = jwt -> jwt.getExpiresAt() != null
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Token has no expiration (exp) claim", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                List.of(JwtValidators.createDefault(), requireExpiry)));
        return decoder;
    }
}
