package com.uba.mbp.referencedataconfig.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * RFP §3.14: a Country's base currency and GL mappings (write-off, recovery),
 * effective-dated rather than a flat current-value row (spec User Story 6 —
 * "a report run against a past period uses the mapping that was in effect
 * then, not today's"). {@code effectiveTo == null} means this is the
 * currently-in-effect row for {@code countryCode}; an edit never mutates a
 * row in place — {@link CountryRepository} enforces exactly one open row per
 * country via a partial unique index (see the V1 migration), and
 * {@code CountryConfigService} closes the old row and inserts a new one in
 * the same transaction.
 */
@Entity
@Table(name = "country_config")
public class Country {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    @Column(nullable = false)
    private String region;

    @Column(name = "base_currency", nullable = false)
    private String baseCurrency;

    @Column(name = "gl_write_off_code", nullable = false)
    private String glWriteOffCode;

    @Column(name = "gl_recovery_code", nullable = false)
    private String glRecoveryCode;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    protected Country() {
    }

    private Country(String countryCode, String region, String baseCurrency, String glWriteOffCode,
                     String glRecoveryCode, Instant effectiveFrom, Instant createdAt, String createdBy) {
        this.countryCode = countryCode;
        this.region = region;
        this.baseCurrency = baseCurrency;
        this.glWriteOffCode = glWriteOffCode;
        this.glRecoveryCode = glRecoveryCode;
        this.effectiveFrom = effectiveFrom;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public static Country open(String countryCode, String region, String baseCurrency, String glWriteOffCode,
                                String glRecoveryCode, Instant now, String createdBy) {
        return new Country(countryCode, region, baseCurrency, glWriteOffCode, glRecoveryCode, now, now, createdBy);
    }

    /** Closes this row as of {@code now} — it stops being the current mapping but stays as history. */
    public void close(Instant now) {
        this.effectiveTo = now;
    }

    public UUID getId() {
        return id;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getRegion() {
        return region;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public String getGlWriteOffCode() {
        return glWriteOffCode;
    }

    public String getGlRecoveryCode() {
        return glRecoveryCode;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
