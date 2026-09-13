package com.uba.mbp.sharedplatform.auth.web;

import com.uba.mbp.sharedplatform.auth.token.SigningKeys;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The public JWKS every downstream {@code JwtDecoder} fetches to verify
 * tokens locally (RFP §3.7 User Story 6) — see ADR-0017.
 */
@RestController
public class JwksController {

    private final SigningKeys signingKeys;

    public JwksController(SigningKeys signingKeys) {
        this.signingKeys = signingKeys;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return signingKeys.publicJwkSet().toJSONObject();
    }
}
