package com.uba.mbp.integration.excelimport.security;

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

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * OAuth2 resource-server security, RBAC-enforced via the {@code roles} JWT claim
 * (see {@link JwtRoleConverter}). Only the Maxim Team role has "manage Excel
 * data integration processes" privilege (RFP §3.7) — enforced at the controller.
 *
 * <p>The {@link #jwtDecoder()} bean uses a symmetric dev secret because
 * shared-platform's authentication-service doesn't exist yet — same interim
 * seam as memo-balance's SecurityConfig.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${security.jwt.dev-secret}")
    private String devSecret;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Stateless bearer-token API, no cookie/session auth — CSRF protection
        // (designed for browser session auth) would otherwise turn an
        // unauthenticated POST's 401 into a 403 before auth is even checked.
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer((OAuth2ResourceServerConfigurer<HttpSecurity> oauth2) -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(new JwtRoleConverter())));
        return http.build();
    }

    /**
     * Enterprise-review finding: {@code JwtValidators.createDefault()}'s
     * timestamp check treats a missing {@code exp} claim as valid — so a
     * token minted without one never expires, and this upload endpoint
     * (a data-modifying action RFP §4.3 wants a real session timeout on)
     * would accept it forever. Requiring {@code exp} to be present closes
     * that gap without waiting on shared-platform's authentication-service.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(
                devSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).build();
        OAuth2TokenValidator<Jwt> requireExpiry = jwt -> jwt.getExpiresAt() != null
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Token has no expiration (exp) claim", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                List.of(JwtValidators.createDefault(), requireExpiry)));
        return decoder;
    }
}
