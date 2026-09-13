package com.uba.mbp.sharedplatform.auth.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The RSA keypair authentication-service signs tokens with, and the JWKS
 * every downstream {@code JwtDecoder} fetches to verify them locally without
 * calling back here per-request (User Story 6).
 *
 * <p><b>Accepted local-dev limitation (see ADR-0017)</b>: this key is
 * generated fresh at startup, not persisted. Restarting authentication-service
 * invalidates every token issued before the restart — every downstream
 * service simply re-fetches the new JWKS and rejects old tokens as
 * unverifiable, which is safe (fails closed), just inconvenient. A real
 * deployment needs a persisted, rotated key or an external KMS; out of scope
 * per ADR-0008's local-first environment.
 */
@Component
public class SigningKeys {

    private final RSAKey rsaKey;

    public SigningKeys() {
        try {
            this.rsaKey = new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .keyUse(KeyUse.SIGNATURE)
                    .generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to generate the RSA signing key", e);
        }
    }

    /** The full keypair (private + public), for signing. Never expose this directly. */
    public RSAKey signingKey() {
        return rsaKey;
    }

    /** The public-only JWK Set served at {@code GET /.well-known/jwks.json}. */
    public JWKSet publicJwkSet() {
        return new JWKSet(rsaKey.toPublicJWK());
    }
}
