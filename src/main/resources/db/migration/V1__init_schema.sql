-- Fintech ledger core.
-- Customer attributes live only on customers (3NF). Account balances are a
-- materialized position; transaction_entries are the immutable audit lines.
-- Currency is repeated on each entry so a posted line stays self-describing.
--
-- Balance convention for every account, including treasury:
--   CREDIT increases balance, DEBIT decreases balance, balance never goes negative.
-- A transfer is one DEBIT and one CREDIT of the same amount. Deposits and
-- withdrawals use the per-currency treasury account as the contra leg. This
-- migration creates an empty schema. Load your own opening balances separately.

create table customers (
    id uuid primary key default gen_random_uuid(),
    email varchar(320) not null,
    full_name varchar(200) not null,
    status varchar(32) not null,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint uq_customers_email unique (email),
    constraint customers_email_lowercase_chk check (email = lower(email)),
    constraint customers_name_chk check (char_length(btrim(full_name)) > 0),
    constraint customers_status_chk check (status in ('ACTIVE', 'SUSPENDED', 'KYC_PENDING'))
);

create table accounts (
    id uuid primary key default gen_random_uuid(),
    customer_id uuid not null references customers (id) on delete restrict,
    currency varchar(3) not null,
    balance numeric(19, 4) not null,
    status varchar(32) not null,
    account_role varchar(32) not null,
    version bigint not null default 0,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint accounts_currency_chk check (currency ~ '^[A-Z]{3}$'),
    constraint accounts_balance_non_negative_chk check (balance >= 0),
    constraint accounts_status_chk check (status in ('ACTIVE', 'FROZEN', 'CLOSED')),
    constraint accounts_role_chk check (account_role in ('CUSTOMER', 'TREASURY'))
);

create index idx_accounts_customer_id on accounts (customer_id);

-- One treasury (contra) account per currency. Customer accounts are not unique
-- per currency: a customer may hold more than one wallet in the same currency.
create unique index uq_accounts_treasury_currency
    on accounts (currency)
    where account_role = 'TREASURY';

create table idempotency_keys (
    id bigserial primary key,
    idempotency_key varchar(200) not null,
    request_hash varchar(64) not null,
    response_payload text,
    created_at timestamp with time zone not null default now(),
    constraint uq_idempotency_key unique (idempotency_key)
);

create index idx_idempotency_keys_created_at on idempotency_keys (created_at);

create table transaction_journals (
    id bigserial primary key,
    reference_id uuid not null,
    type varchar(32) not null,
    status varchar(32) not null,
    memo varchar(280),
    idempotency_key_id bigint references idempotency_keys (id) on delete restrict,
    created_at timestamp with time zone not null default now(),
    constraint uq_journals_reference_id unique (reference_id),
    constraint journals_type_chk check (type in ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    constraint journals_status_chk check (status in ('PENDING', 'COMPLETED', 'FAILED'))
);

create index idx_journals_created_at on transaction_journals (created_at);

create table transaction_entries (
    id bigserial primary key,
    journal_id bigint not null references transaction_journals (id) on delete restrict,
    account_id uuid not null references accounts (id) on delete restrict,
    amount numeric(19, 4) not null,
    entry_type varchar(32) not null,
    currency varchar(3) not null,
    constraint entries_amount_positive_chk check (amount > 0),
    constraint entries_type_chk check (entry_type in ('DEBIT', 'CREDIT')),
    constraint entries_currency_chk check (currency ~ '^[A-Z]{3}$')
);

create index idx_entries_journal_id on transaction_entries (journal_id);
create index idx_entries_account_id_id on transaction_entries (account_id, id desc);

-- Immediate guard: an entry cannot name a different currency than its account.
create or replace function enforce_entry_account_currency()
returns trigger
language plpgsql
as $$
declare
    account_currency varchar(3);
begin
    select currency into account_currency from accounts where id = new.account_id;
    if account_currency is null then
        raise exception 'Account % does not exist', new.account_id;
    end if;
    if account_currency <> new.currency then
        raise exception 'Entry currency % does not match account currency %', new.currency, account_currency;
    end if;
    return new;
end;
$$;

create trigger trg_entry_account_currency
before insert on transaction_entries
for each row
execute function enforce_entry_account_currency();

-- Posted lines are an audit log. Corrections are new journals, never updates.
create or replace function prevent_entry_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'transaction_entries are immutable';
end;
$$;

create trigger trg_entries_immutable
before update or delete on transaction_entries
for each row
execute function prevent_entry_mutation();

-- Deferred until commit so the journal row can be inserted before its two
-- lines. An immediate trigger would reject every journal. Exactly two lines,
-- and debits must equal credits.
create or replace function enforce_balanced_journal()
returns trigger
language plpgsql
as $$
declare
    entry_count bigint;
    debit_sum numeric(19, 4);
    credit_sum numeric(19, 4);
begin
    select count(*),
           coalesce(sum(amount) filter (where entry_type = 'DEBIT'), 0),
           coalesce(sum(amount) filter (where entry_type = 'CREDIT'), 0)
      into entry_count, debit_sum, credit_sum
    from transaction_entries
    where journal_id = new.id;

    if entry_count <> 2 then
        raise exception 'Journal % must contain exactly two entries (found %)', new.id, entry_count;
    end if;
    if debit_sum <> credit_sum then
        raise exception 'Journal % is unbalanced (debits %, credits %)', new.id, debit_sum, credit_sum;
    end if;
    return null;
end;
$$;

create constraint trigger trg_balanced_journal
after insert on transaction_journals
deferrable initially deferred
for each row
execute function enforce_balanced_journal();

comment on table transaction_entries is 'Immutable double-entry lines. Each journal has exactly one debit and one credit of equal amount.';
comment on column accounts.account_role is 'CUSTOMER wallets are API-visible. TREASURY is the per-currency contra account for deposits and withdrawals.';
comment on column transaction_journals.memo is 'Optional operator narrative. Not required for the accounting identity.';
