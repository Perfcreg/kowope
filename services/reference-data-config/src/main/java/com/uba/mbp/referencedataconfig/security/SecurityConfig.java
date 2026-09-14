package com.uba.mbp.referencedataconfig.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
 * claim (see {@link JwtRoleConverter}) — RS256 tokens verified via
 * shared-platform's authentication-service JWKS endpoint (ADR-0017). Unlike
 * every other resource-server copy in this repo, the read path
 * ({@code GET /countries/**}) is deliberately {@code permitAll} — ADR-0021,
 * same trust model as notification-service's {@code POST /notifications}
 * (ADR-0019): called service-to-service, not by a human or the {@code
 * channels} SPA. Only the admin-write path requires {@code hasRole("ADMIN")}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Stateless bearer-token API, no cookie/session auth — CSRF protection
        // (designed for browser session auth) would otherwise turn an
        // unauthenticated write's 401 into a 403 before auth is even checked.
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/countries/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer((OAuth2ResourceServerConfigurer<HttpSecurity> oauth2) -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(new JwtRoleConverter())));
        return http.build();
    }

    /**
     * {@code JwtValidators.createDefault()}'s timestamp check treats a
     * missing {@code exp} claim as valid — requiring it closes that gap for
     * this admin-write, config-modifying endpoint (RFP §4.3 session-timeout
     * intent), same fix already applied in excel-import-service's copy.
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
