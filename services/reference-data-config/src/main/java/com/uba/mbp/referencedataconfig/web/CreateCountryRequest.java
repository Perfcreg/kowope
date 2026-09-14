package com.uba.mbp.referencedataconfig.web;

public record CreateCountryRequest(String countryCode, String region, String baseCurrency,
                                    String glWriteOffCode, String glRecoveryCode) {
}
