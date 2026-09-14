package com.uba.mbp.referencedataconfig.web;

public record UpdateCountryRequest(String region, String baseCurrency, String glWriteOffCode, String glRecoveryCode) {
}
