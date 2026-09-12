package com.uba.mbp.memobalance.referencedata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Ticket 08: the Country/GL mapping lookup, tested at its own seam (no Spring context needed). */
class StaticCountryConfigLookupTest {

    private final StaticCountryConfigLookup lookup = new StaticCountryConfigLookup();

    @Test
    void resolvesNigeriasConfiguredMapping() {
        CountryConfig config = lookup.lookup("NG");

        assertEquals("NGN", config.baseCurrency());
        assertEquals("GL-WRITEOFF-NG", config.glWriteOffCode());
        assertEquals("GL-RECOVERY-NG", config.glRecoveryCode());
    }

    @Test
    void fallsBackToTheDefaultForAnUnconfiguredCountry() {
        CountryConfig config = lookup.lookup("ZZ");

        assertEquals("NGN", config.baseCurrency());
    }
}
