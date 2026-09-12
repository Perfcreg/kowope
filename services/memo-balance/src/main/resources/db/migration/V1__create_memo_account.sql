create table memo_account (
    id uuid primary key,
    account_number varchar(64) not null unique,
    customer_id varchar(64),
    branch_sol varchar(32),
    currency varchar(3) not null,
    posting_reference varchar(128),
    narration text not null,
    balance numeric(19, 4) not null,
    transfer_date timestamptz not null,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_memo_account_status on memo_account (status);
