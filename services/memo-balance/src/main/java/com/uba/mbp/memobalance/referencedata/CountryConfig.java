package com.uba.mbp.memobalance.referencedata;

/** RFP §3.14: the per-Country configuration a Memo record needs at detection time. */
public record CountryConfig(String countryCode, String baseCurrency, String glWriteOffCode, String glRecoveryCode) {
}
