package com.uba.mbp.referencedataconfig.web;

import com.uba.mbp.referencedataconfig.service.CountryConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * RFP §3.14 User Story 1 ("As an Administrator, I want to add or edit a
 * Country and its mappings... without a code change"). ADMIN-gated (ADR-0018)
 * — the acting admin is taken from their own verified token's {@code sub}
 * claim, never a request field, same discipline as authentication-service's
 * {@code AdminUserController}.
 */
@RestController
public class CountryAdminController {

    private final CountryConfigService service;

    public CountryAdminController(CountryConfigService service) {
        this.service = service;
    }

    @PostMapping("/countries")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CountryResponse> create(@RequestBody CreateCountryRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        var created = service.create(request.countryCode(), request.region(), request.baseCurrency(),
                request.glWriteOffCode(), request.glRecoveryCode(), jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(CountryResponse.from(created));
    }

    @PutMapping("/countries/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public CountryResponse update(@PathVariable("code") String countryCode,
                                   @RequestBody UpdateCountryRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        var updated = service.update(countryCode, request.region(), request.baseCurrency(),
                request.glWriteOffCode(), request.glRecoveryCode(), jwt.getSubject());
        return CountryResponse.from(updated);
    }
}
