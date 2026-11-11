-- System accounts used as the counterpart of user movements (one per currency).
INSERT INTO accounts (id, owner_id, code, currency, kind, status, balance, created_at)
VALUES ('00000000-0000-0000-0000-00000000a001', NULL, 'SETTLEMENT_ARS', 'ARS', 'SETTLEMENT', 'ACTIVE', 0, now()),
       ('00000000-0000-0000-0000-00000000a002', NULL, 'SETTLEMENT_USD', 'USD', 'SETTLEMENT', 'ACTIVE', 0, now()),
       ('00000000-0000-0000-0000-00000000f001', NULL, 'FX_POOL_ARS', 'ARS', 'FX_POOL', 'ACTIVE', 0, now()),
       ('00000000-0000-0000-0000-00000000f002', NULL, 'FX_POOL_USD', 'USD', 'FX_POOL', 'ACTIVE', 0, now());
