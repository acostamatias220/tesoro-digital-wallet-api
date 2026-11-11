-- Tesoro: initial schema
-- Money is stored as NUMERIC (never floating point). Wallet balances use the currency scale (2).

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email))
);

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id  UUID        NOT NULL,
    token_hash VARCHAR(64)    NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);

-- Ledger accounts: user wallets (one per user and currency) and system accounts.
CREATE TABLE accounts (
    id         UUID PRIMARY KEY,
    owner_id   UUID REFERENCES users (id),
    code       VARCHAR(40),
    currency   VARCHAR(3)    NOT NULL CHECK (currency IN ('ARS', 'USD')),
    kind       VARCHAR(16)   NOT NULL CHECK (kind IN ('USER_WALLET', 'SETTLEMENT', 'FX_POOL')),
    status     VARCHAR(16)   NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN')),
    -- Cached balance, maintained only for USER_WALLET (system accounts derive it from postings).
    balance    NUMERIC(19, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_accounts_id_currency UNIQUE (id, currency),
    CONSTRAINT uq_accounts_code UNIQUE (code),
    CONSTRAINT ck_accounts_wallet_owner CHECK ((kind = 'USER_WALLET') = (owner_id IS NOT NULL)),
    CONSTRAINT ck_accounts_system_code CHECK ((kind = 'USER_WALLET') = (code IS NULL)),
    -- Last line of defence against double spending.
    CONSTRAINT ck_accounts_wallet_non_negative CHECK (kind <> 'USER_WALLET' OR balance >= 0)
);
CREATE UNIQUE INDEX uq_accounts_wallet_per_currency ON accounts (owner_id, currency) WHERE kind = 'USER_WALLET';

-- One row per business operation.
CREATE TABLE journal_entries (
    id           UUID PRIMARY KEY,
    type         VARCHAR(16)  NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'CONVERSION')),
    description  VARCHAR(140) NOT NULL,
    initiated_by UUID REFERENCES users (id),
    metadata     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_journal_entries_created_at ON journal_entries (created_at DESC);

-- Debit/credit lines. Amounts are always positive; the direction gives the sign.
CREATE TABLE postings (
    id               BIGSERIAL PRIMARY KEY,
    journal_entry_id UUID           NOT NULL REFERENCES journal_entries (id),
    account_id       UUID           NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    direction        VARCHAR(6)     NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount           NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    -- Wallet balance right after this posting (NULL for system accounts).
    balance_after    NUMERIC(19, 2),
    created_at       TIMESTAMPTZ    NOT NULL,
    -- A posting can only be in the currency of its account.
    CONSTRAINT fk_postings_account_currency FOREIGN KEY (account_id, currency) REFERENCES accounts (id, currency)
);
CREATE INDEX ix_postings_account_created ON postings (account_id, created_at DESC, id DESC);
CREATE INDEX ix_postings_entry ON postings (journal_entry_id);

-- Idempotency keys, scoped per user. The stored response is replayed on retries.
CREATE TABLE idempotency_keys (
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    idem_key      VARCHAR(100) NOT NULL,
    request_hash  VARCHAR(64)     NOT NULL,
    response_body JSONB,
    created_at    TIMESTAMPTZ  NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (user_id, idem_key)
);
CREATE INDEX ix_idempotency_keys_expires ON idempotency_keys (expires_at);

-- Transactional outbox: written in the same transaction as the change it describes.
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(40)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(60)  NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ,
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     VARCHAR(500)
);
CREATE INDEX ix_outbox_pending ON outbox_events (created_at) WHERE published_at IS NULL;

CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_id    UUID,
    action      VARCHAR(60) NOT NULL,
    target_type VARCHAR(40),
    target_id   VARCHAR(64),
    ip_address  VARCHAR(45),
    details     JSONB       NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX ix_audit_log_occurred ON audit_log (occurred_at DESC);
CREATE INDEX ix_audit_log_action ON audit_log (action, occurred_at DESC);

CREATE TABLE reconciliation_runs (
    id              UUID PRIMARY KEY,
    trigger_source  VARCHAR(20) NOT NULL,
    status          VARCHAR(16) NOT NULL CHECK (status IN ('OK', 'MISMATCH')),
    wallets_checked BIGINT      NOT NULL,
    entries_checked BIGINT      NOT NULL,
    issues          JSONB       NOT NULL DEFAULT '[]'::jsonb,
    started_at      TIMESTAMPTZ NOT NULL,
    finished_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_reconciliation_runs_started ON reconciliation_runs (started_at DESC);
