create table balance_adjustment (
    id uuid primary key,
    memo_account_id uuid not null references memo_account (id),
    adjustment_type varchar(32) not null,
    previous_balance numeric(19, 4) not null,
    new_balance numeric(19, 4) not null,
    occurred_at timestamptz not null
);

create index idx_balance_adjustment_memo_account_id on balance_adjustment (memo_account_id);
