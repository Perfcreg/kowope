package com.uba.mbp.memobalance.security;

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
 * OAuth2 resource-server security, RBAC-enforced via the {@code roles} JWT claim
 * (see {@link JwtRoleConverter}). Ticket 03: Country-scoping (RFP §3.14) is NOT
 * enforced yet — that needs reference-data-config's Country model, which doesn't
 * exist yet; role-based access is the full scope of what's implemented here.
 *
 * <p>The {@link #jwtDecoder()} bean uses a symmetric dev secret because
 * shared-platform's authentication-service (a real token issuer) doesn't exist
 * yet. Swap this for an issuer-uri/jwk-set-uri-based decoder once it does — the
 * rest of this config (the converter, the authorization rules) doesn't change.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${security.jwt.dev-secret}")
    private String devSecret;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
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
