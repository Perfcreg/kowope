package com.uba.mbp.referencedataconfig.service;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.referencedataconfig.domain.Country;
import com.uba.mbp.referencedataconfig.domain.CountryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * RFP §3.14: create/edit a Country's base currency and GL mappings. Every
 * edit is effective-dated (spec User Story 6) — {@link #update} never
 * mutates a row in place, it closes the current one and opens a new one in
 * the same transaction, so a lookup for a past instant (once `reporting`
 * needs it) still resolves the mapping that was actually in effect then.
 */
@Service
public class CountryConfigService {

    private static final String SOURCE = "reference-data-config";

    private final CountryRepository repository;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public CountryConfigService(CountryRepository repository, AuditLogger auditLogger, Clock clock) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional
    public Country create(String countryCode, String region, String baseCurrency, String glWriteOffCode,
                           String glRecoveryCode, String actor) {
        requireNonBlank(countryCode, "countryCode");
        requireNonBlank(region, "region");
        requireNonBlank(baseCurrency, "baseCurrency");
        requireNonBlank(glWriteOffCode, "glWriteOffCode");
        requireNonBlank(glRecoveryCode, "glRecoveryCode");

        if (repository.existsByCountryCodeAndEffectiveToIsNull(countryCode)) {
            throw new CountryAlreadyExistsException(countryCode);
        }

        Country created = repository.save(Country.open(
                countryCode, region, baseCurrency, glWriteOffCode, glRecoveryCode, clock.instant(), actor));

        auditLogger.record(new AuditEvent(
                clock.instant(), actor, "COUNTRY_CONFIG_CREATED", "Country", countryCode,
                SOURCE, "region=" + region + " baseCurrency=" + baseCurrency));

        return created;
    }

    @Transactional
    public Country update(String countryCode, String region, String baseCurrency, String glWriteOffCode,
                           String glRecoveryCode, String actor) {
        requireNonBlank(region, "region");
        requireNonBlank(baseCurrency, "baseCurrency");
        requireNonBlank(glWriteOffCode, "glWriteOffCode");
        requireNonBlank(glRecoveryCode, "glRecoveryCode");

        Country current = repository.findByCountryCodeAndEffectiveToIsNull(countryCode)
                .orElseThrow(() -> new CountryNotFoundException(countryCode));

        Instant now = clock.instant();
        current.close(now);
        // Flushed before the new row's insert: the partial unique index (one
        // open row per country) is checked per-statement, not deferred to
        // commit — the old row's effective_to must actually hit the database
        // before the new row's insert, or the insert trips the constraint.
        repository.saveAndFlush(current);

        Country updated = repository.save(Country.open(
                countryCode, region, baseCurrency, glWriteOffCode, glRecoveryCode, now, actor));

        auditLogger.record(new AuditEvent(
                now, actor, "COUNTRY_CONFIG_UPDATED", "Country", countryCode,
                SOURCE, "region=" + region + " baseCurrency=" + baseCurrency));

        return updated;
    }

    @Transactional(readOnly = true)
    public Country findCurrent(String countryCode) {
        return repository.findByCountryCodeAndEffectiveToIsNull(countryCode)
                .orElseThrow(() -> new CountryNotFoundException(countryCode));
    }

    @Transactional(readOnly = true)
    public List<Country> listCurrent() {
        return repository.findAllByEffectiveToIsNull();
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
