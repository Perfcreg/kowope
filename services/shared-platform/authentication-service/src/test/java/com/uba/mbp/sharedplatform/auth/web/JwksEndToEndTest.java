package com.uba.mbp.sharedplatform.auth.web;

import com.uba.mbp.sharedplatform.auth.AbstractIntegrationTest;
import com.uba.mbp.sharedplatform.auth.domain.Role;
import com.uba.mbp.sharedplatform.auth.token.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The exact path every downstream {@code JwtDecoder} uses in production:
 * {@code NimbusJwtDecoder.withJwkSetUri(...)} fetching the public JWK Set
 * over real HTTP from this service's own running {@code /.well-known/jwks.json}
 * — not the in-process public-key shortcut {@code TokenServiceTest} uses.
 * Proves the JWKS wiring itself, end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JwksEndToEndTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TokenService tokenService;

    @Test
    void aTokenIssuedByTokenServiceVerifiesAgainstTheLiveJwksEndpoint() {
        String token = tokenService.issueAccessToken("jwks-e2e-user", EnumSet.of(Role.RECOVERY_TEAM));

        String jwkSetUri = "http://localhost:" + port + "/.well-known/jwks.json";
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        Jwt decoded = decoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo("jwks-e2e-user");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("RECOVERY_TEAM");
        assertThat(decoded.getTokenValue()).isEqualTo(token);
    }
}
