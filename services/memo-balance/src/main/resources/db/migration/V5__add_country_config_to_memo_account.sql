alter table memo_account
    add column country varchar(3),
    add column base_currency varchar(3),
    add column gl_write_off_code varchar(32),
    add column gl_recovery_code varchar(32);
