package com.uba.mbp.sharedplatform.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * authentication-service issues tokens rather than validating them, so it has
 * no {@code JwtDecoder}/resource-server config of its own — unlike every
 * downstream service's {@code SecurityConfig}. Its own endpoints
 * (login/MFA/refresh/step-up/JWKS) are deliberately public: they're how a
 * caller obtains a token in the first place.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Stateless bearer-token issuer, no cookie/session auth — CSRF doesn't apply.
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/.well-known/**").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
