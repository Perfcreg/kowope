package com.uba.mbp.referencedataconfig.web;

import com.uba.mbp.referencedataconfig.domain.Country;

import java.time.Instant;

/** The currently-effective mapping only — {@code effectiveTo} is always null here by construction (see CountryConfigService). */
public record CountryResponse(String countryCode, String region, String baseCurrency,
                               String glWriteOffCode, String glRecoveryCode, Instant effectiveFrom) {

    public static CountryResponse from(Country country) {
        return new CountryResponse(country.getCountryCode(), country.getRegion(), country.getBaseCurrency(),
                country.getGlWriteOffCode(), country.getGlRecoveryCode(), country.getEffectiveFrom());
    }
}
