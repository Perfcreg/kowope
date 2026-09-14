create table country_config (
    id uuid primary key,
    country_code varchar(8) not null,
    region varchar(64) not null,
    base_currency varchar(3) not null,
    gl_write_off_code varchar(64) not null,
    gl_recovery_code varchar(64) not null,
    effective_from timestamptz not null,
    effective_to timestamptz,
    created_at timestamptz not null,
    created_by varchar(64) not null
);

-- At most one currently-effective row per country — the app-level
-- close-and-insert transaction (CountryConfigService.update) relies on this
-- to actually be enforced, not just assumed.
create unique index idx_country_config_current on country_config (country_code) where effective_to is null;

-- RFP §3.14's default Region ("Africa & Nigeria") — matches the values
-- memo-balance's StaticCountryConfigLookup stand-in already used, so
-- swapping the seam doesn't change any existing behavior.
insert into country_config (id, country_code, region, base_currency, gl_write_off_code, gl_recovery_code,
                             effective_from, effective_to, created_at, created_by)
values (gen_random_uuid(), 'NG', 'Africa & Nigeria', 'NGN', 'GL-WRITEOFF-NG', 'GL-RECOVERY-NG',
        now(), null, now(), 'system-seed');
