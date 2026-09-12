create table memo_document (
    id uuid primary key,
    memo_account_id uuid not null references memo_account (id),
    file_name varchar(255) not null,
    content_type varchar(128) not null,
    storage_key varchar(512) not null,
    uploaded_by varchar(128) not null,
    uploaded_at timestamptz not null
);

create index idx_memo_document_memo_account_id on memo_document (memo_account_id);
