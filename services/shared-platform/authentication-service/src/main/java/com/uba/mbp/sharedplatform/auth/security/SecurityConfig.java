package com.uba.mbp.sharedplatform.auth.security;

import com.nimbusds.jose.JOSEException;
import com.uba.mbp.sharedplatform.auth.token.SigningKeys;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.security.interfaces.RSAPublicKey;

/**
 * authentication-service mostly issues tokens rather than validating them —
 * {@code /auth/**} and {@code /.well-known/**} are deliberately public,
 * that's how a caller obtains a token in the first place. Its own
 * {@code /admin/**} routes (ADR-0018) are the exception: this service
 * becomes a resource server for exactly those, validating callers'
 * tokens itself. It already holds the RSA keypair ({@link SigningKeys}), so
 * it decodes with its own public key directly rather than fetching its own
 * JWKS over HTTP.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Stateless bearer-token issuer, no cookie/session auth — CSRF doesn't apply.
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/.well-known/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer((OAuth2ResourceServerConfigurer<HttpSecurity> oauth2) -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(new JwtRoleConverter())));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(SigningKeys signingKeys) {
        try {
            RSAPublicKey publicKey = (RSAPublicKey) signingKeys.signingKey().toRSAPublicKey();
            return NimbusJwtDecoder.withPublicKey(publicKey).build();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to derive the public key from the signing key", e);
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
