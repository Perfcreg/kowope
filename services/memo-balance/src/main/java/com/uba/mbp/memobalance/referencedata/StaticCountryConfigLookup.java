package com.uba.mbp.memobalance.referencedata;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Dev/test stand-in for {@code reference-data-config}: a fixed, in-memory
 * mapping table rather than a real versioned lookup. RFP §3.14's Region
 * default (Africa & Nigeria) is the only entry seeded — add Countries here
 * only until the real context exists; don't grow this into a config system.
 */
@Component
public class StaticCountryConfigLookup implements CountryConfigLookup {

    private static final Map<String, CountryConfig> CONFIGS = Map.of(
            "NG", new CountryConfig("NG", "NGN", "GL-WRITEOFF-NG", "GL-RECOVERY-NG"));

    private static final CountryConfig DEFAULT = CONFIGS.get("NG");

    @Override
    public CountryConfig lookup(String countryCode) {
        // countryCode is optional on MemoDetectedEvent (a producer on an older
        // schema version may not send it yet) — null must fall back, not throw.
        if (countryCode == null) {
            return DEFAULT;
        }
        return CONFIGS.getOrDefault(countryCode, DEFAULT);
    }
}
