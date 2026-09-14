package com.uba.mbp.referencedataconfig.service;

/** A currently-effective mapping already exists for this country code — use update, not create. */
public class CountryAlreadyExistsException extends RuntimeException {
    public CountryAlreadyExistsException(String countryCode) {
        super("A current mapping already exists for country: " + countryCode);
    }
}
