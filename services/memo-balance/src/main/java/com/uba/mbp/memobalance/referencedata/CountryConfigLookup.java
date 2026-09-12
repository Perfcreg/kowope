package com.uba.mbp.memobalance.referencedata;

/**
 * The seam onto the {@code reference-data-config} context (RFP §3.14), which
 * doesn't exist yet — {@link StaticCountryConfigLookup} stands in for it.
 * Swap the implementation, not this interface or its callers, once that
 * context is real and can serve versioned, effective-dated mappings over REST.
 */
public interface CountryConfigLookup {

    /** The mapping in effect for {@code countryCode} right now. */
    CountryConfig lookup(String countryCode);
}
