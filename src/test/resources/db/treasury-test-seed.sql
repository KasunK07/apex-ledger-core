insert into customers (id, email, full_name, status, created_at, updated_at)
values (
    '00000000-0000-0000-0000-000000000001',
    'treasury@ledger.internal',
    'Ledger Treasury',
    'ACTIVE',
    now(),
    now()
)
on conflict (id) do nothing;

insert into accounts (
    id, customer_id, currency, balance, status, account_role, version, created_at, updated_at
) values
    ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-000000000001', 'USD', 1000000000.0000, 'ACTIVE', 'TREASURY', 0, now(), now()),
    ('00000000-0000-0000-0000-0000000000a2', '00000000-0000-0000-0000-000000000001', 'EUR', 1000000000.0000, 'ACTIVE', 'TREASURY', 0, now(), now()),
    ('00000000-0000-0000-0000-0000000000a3', '00000000-0000-0000-0000-000000000001', 'LKR', 1000000000.0000, 'ACTIVE', 'TREASURY', 0, now(), now())
on conflict (id) do nothing;
