package com.uba.mbp.memobalance.security;

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
 * OAuth2 resource-server security, RBAC-enforced via the {@code roles} JWT claim
 * (see {@link JwtRoleConverter}). Ticket 03: Country-scoping (RFP §3.14) is NOT
 * enforced yet — that needs reference-data-config's Country model, which doesn't
 * exist yet; role-based access is the full scope of what's implemented here.
 *
 * <p>The {@link #jwtDecoder()} bean validates real tokens via shared-platform's
 * authentication-service JWKS endpoint (ADR-0017) — this replaces the previous
 * symmetric dev-secret decoder now that a real token issuer exists.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer((OAuth2ResourceServerConfigurer<HttpSecurity> oauth2) -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(new JwtRoleConverter())));
        return http.build();
    }

    /**
     * System-wide-audit fix (2026-09-15): {@code JwtValidators.createDefault()}'s
     * timestamp check treats a missing {@code exp} claim as valid — a token
     * minted without one would never expire on this context's endpoints (real
     * account/balance data, RFP §4.3's session-timeout intent). Same fix
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
