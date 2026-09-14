package com.uba.mbp.referencedataconfig.service;

/** No currently-effective mapping exists for the given country code. */
public class CountryNotFoundException extends RuntimeException {
    public CountryNotFoundException(String countryCode) {
        super("No current mapping for country: " + countryCode);
    }
}
