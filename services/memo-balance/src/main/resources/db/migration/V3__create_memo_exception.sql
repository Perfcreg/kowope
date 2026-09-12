create table memo_exception (
    id uuid primary key,
    memo_account_id uuid not null references memo_account (id),
    type varchar(32) not null,
    detail text not null,
    raised_at timestamptz not null
);

create index idx_memo_exception_memo_account_id on memo_exception (memo_account_id);
