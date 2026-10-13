-- Example only. Flyway does not run this file.
-- Copy it, set your own ids, currencies, and opening balances, then run it
-- against your database after V1 has been applied.
--
-- Deposits and withdrawals need one TREASURY account per currency.
-- Keep these ids in sync with com.ledger.domain.model.SystemParties
-- if you use the built-in treasury customer id.

insert into customers (id, email, full_name, status, created_at, updated_at)
values (
    '00000000-0000-0000-0000-000000000001',
    'treasury@ledger.internal',
    'Ledger Treasury',
    'ACTIVE',
    now(),
    now()
);

insert into accounts (
    id, customer_id, currency, balance, status, account_role, version, created_at, updated_at
) values
    ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-000000000001', 'USD', 0.0000, 'ACTIVE', 'TREASURY', 0, now(), now()),
    ('00000000-0000-0000-0000-0000000000a2', '00000000-0000-0000-0000-000000000001', 'EUR', 0.0000, 'ACTIVE', 'TREASURY', 0, now(), now()),
    ('00000000-0000-0000-0000-0000000000a3', '00000000-0000-0000-0000-000000000001', 'LKR', 0.0000, 'ACTIVE', 'TREASURY', 0, now(), now());
