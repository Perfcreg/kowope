package com.uba.mbp.integration.icad.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * OAuth2 resource-server security, RBAC-enforced via the {@code roles} JWT
 * claim (see {@link JwtRoleConverter}). Only Credit Admin / Recovery Team have
 * "full privileges — verification, liquidation tracking, reporting" (RFP
 * §3.7) — the two roles that would trigger an ICAD clearance request.
 *
 * <p>Enterprise-review gap (2026-09-12), NOT fixed here, same as memo-balance's
 * SecurityConfig deferring Country-scoping: RFP §4.3's MFA and
 * sensitive-action session-timeout requirements aren't enforced — a role
 * claim alone authorizes pushing a clearance to ICAD, an irreversible
 * external-system mutation. There is no {@code amr}/{@code acr} claim
 * convention anywhere in this repo yet, and no shorter-timeout mechanism
 * distinct from the JWT's own {@code exp}, to build against. Both need
 * shared-platform's authentication-service to exist first, same as the
 * dev-secret {@link #jwtDecoder()} below.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${security.jwt.dev-secret}")
    private String devSecret;

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

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(
                devSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
