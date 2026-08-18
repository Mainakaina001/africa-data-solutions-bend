-- Africa Data Solutions — initial schema.
-- Mirrors backend/prisma/schema.prisma (Prisma) 1:1, including the
-- security-hardening additions (refresh_tokens, webhook_events, outbox_events,
-- audit_logs, wallet velocity columns) and the later removal of the FK from
-- data_orders.data_plan_id (plans are looked up by our own catalog, not a hard
-- foreign key, since upstream plan IDs can be retired independently).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ─── users ──────────────────────────────────────────────────────────────────

CREATE TABLE users (
    id                        UUID PRIMARY KEY,
    email                     TEXT NOT NULL UNIQUE,
    phone                     TEXT NOT NULL UNIQUE,
    first_name                TEXT NOT NULL,
    last_name                 TEXT NOT NULL,
    password                  TEXT NOT NULL,
    transaction_pin           TEXT,
    reset_token_hash          TEXT,
    reset_token_expiry        TIMESTAMPTZ,
    reset_token_attempts      INTEGER NOT NULL DEFAULT 0,
    role                      VARCHAR(20) NOT NULL DEFAULT 'USER',
    token_version             INTEGER NOT NULL DEFAULT 0,
    failed_login_attempts     INTEGER NOT NULL DEFAULT 0,
    locked_until              TIMESTAMPTZ,
    failed_pin_attempts       INTEGER NOT NULL DEFAULT 0,
    pin_locked_until          TIMESTAMPTZ,
    last_login_at             TIMESTAMPTZ,
    last_login_ip             TEXT,
    two_factor_enabled        BOOLEAN NOT NULL DEFAULT false,
    two_factor_secret         TEXT,
    two_factor_backup_codes   TEXT[] NOT NULL DEFAULT '{}',
    fcm_token                 TEXT,
    is_active                 BOOLEAN NOT NULL DEFAULT true,
    is_verified               BOOLEAN NOT NULL DEFAULT false,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX users_role_idx ON users (role);

-- ─── refresh_tokens ─────────────────────────────────────────────────────────

CREATE TABLE refresh_tokens (
    id               UUID PRIMARY KEY,
    user_id          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash       TEXT NOT NULL UNIQUE,
    family           TEXT NOT NULL,
    expires_at       TIMESTAMPTZ NOT NULL,
    revoked_at       TIMESTAMPTZ,
    replaced_by_id   UUID,
    user_agent       TEXT,
    ip               TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens (user_id);
CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family);
CREATE INDEX refresh_tokens_expires_at_idx ON refresh_tokens (expires_at);

-- ─── wallets ────────────────────────────────────────────────────────────────

CREATE TABLE wallets (
    id                       UUID PRIMARY KEY,
    user_id                  UUID NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    balance                  NUMERIC(15, 2) NOT NULL DEFAULT 0,
    currency                 VARCHAR(3) NOT NULL DEFAULT 'NGN',
    version                  INTEGER NOT NULL DEFAULT 0,
    daily_debit_total        NUMERIC(15, 2) NOT NULL DEFAULT 0,
    daily_debit_reset_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE wallet_transactions (
    id               UUID PRIMARY KEY,
    wallet_id        UUID NOT NULL REFERENCES wallets (id) ON DELETE CASCADE,
    type             VARCHAR(10) NOT NULL,
    amount           NUMERIC(15, 2) NOT NULL,
    balance_before   NUMERIC(15, 2) NOT NULL,
    balance_after    NUMERIC(15, 2) NOT NULL,
    reference        TEXT NOT NULL UNIQUE,
    description      TEXT NOT NULL,
    status           VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    metadata         JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX wallet_transactions_wallet_id_idx ON wallet_transactions (wallet_id);
CREATE INDEX wallet_transactions_reference_idx ON wallet_transactions (reference);
CREATE INDEX wallet_transactions_created_at_idx ON wallet_transactions (created_at);

-- ─── catalog ────────────────────────────────────────────────────────────────

CREATE TABLE data_plans (
    id                  UUID PRIMARY KEY,
    network             TEXT NOT NULL,
    network_id          INTEGER NOT NULL,
    sme_plug_plan_id    INTEGER NOT NULL,
    plan_code           TEXT NOT NULL UNIQUE,
    plan_name           TEXT NOT NULL,
    data_amount         TEXT NOT NULL,
    price               NUMERIC(10, 2) NOT NULL,
    telco_price         NUMERIC(10, 2),
    validity            TEXT NOT NULL,
    plan_type           TEXT NOT NULL DEFAULT 'SME',
    is_active           BOOLEAN NOT NULL DEFAULT true,
    description         TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX data_plans_network_idx ON data_plans (network);
CREATE INDEX data_plans_is_active_idx ON data_plans (is_active);
CREATE INDEX data_plans_sme_plug_plan_id_idx ON data_plans (sme_plug_plan_id);

-- ─── orders ─────────────────────────────────────────────────────────────────

CREATE TABLE data_orders (
    id                    UUID PRIMARY KEY,
    user_id               UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    data_plan_id          UUID,
    phone                 TEXT NOT NULL,
    amount                NUMERIC(10, 2) NOT NULL,
    reference             TEXT NOT NULL UNIQUE,
    status                VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    sme_plug_response     JSONB,
    wallet_txn_ref        TEXT UNIQUE,
    refund_txn_ref        TEXT UNIQUE,
    failure_reason        TEXT,
    delivered_at          TIMESTAMPTZ,
    last_reconciled_at    TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX data_orders_user_id_idx ON data_orders (user_id);
CREATE INDEX data_orders_reference_idx ON data_orders (reference);
CREATE INDEX data_orders_status_idx ON data_orders (status);
CREATE INDEX data_orders_created_at_idx ON data_orders (created_at);

CREATE TABLE airtime_orders (
    id                    UUID PRIMARY KEY,
    user_id               UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    network               TEXT NOT NULL,
    phone                 TEXT NOT NULL,
    amount                NUMERIC(10, 2) NOT NULL,
    reference             TEXT NOT NULL UNIQUE,
    vtpass_request_id     TEXT UNIQUE,
    status                VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    vtpass_response       JSONB,
    wallet_txn_ref        TEXT UNIQUE,
    refund_txn_ref        TEXT UNIQUE,
    failure_reason        TEXT,
    delivered_at          TIMESTAMPTZ,
    last_reconciled_at    TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX airtime_orders_user_id_idx ON airtime_orders (user_id);
CREATE INDEX airtime_orders_reference_idx ON airtime_orders (reference);
CREATE INDEX airtime_orders_status_idx ON airtime_orders (status);
CREATE INDEX airtime_orders_created_at_idx ON airtime_orders (created_at);

CREATE TABLE bill_payments (
    id                    UUID PRIMARY KEY,
    user_id               UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category              VARCHAR(12) NOT NULL,
    service_id            TEXT NOT NULL,
    variation_code        TEXT NOT NULL,
    billers_code          TEXT NOT NULL,
    phone                 TEXT NOT NULL,
    amount                NUMERIC(10, 2) NOT NULL,
    reference             TEXT NOT NULL UNIQUE,
    status                VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    vtpass_response       JSONB,
    purchased_token       TEXT,
    wallet_txn_ref        TEXT UNIQUE,
    refund_txn_ref        TEXT UNIQUE,
    failure_reason        TEXT,
    delivered_at          TIMESTAMPTZ,
    last_reconciled_at    TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX bill_payments_user_id_idx ON bill_payments (user_id);
CREATE INDEX bill_payments_reference_idx ON bill_payments (reference);
CREATE INDEX bill_payments_category_idx ON bill_payments (category);
CREATE INDEX bill_payments_status_idx ON bill_payments (status);
CREATE INDEX bill_payments_created_at_idx ON bill_payments (created_at);

-- ─── virtual accounts ───────────────────────────────────────────────────────

CREATE TABLE virtual_accounts (
    id                   UUID PRIMARY KEY,
    user_id              UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_reference    TEXT NOT NULL UNIQUE,
    account_number       TEXT NOT NULL,
    account_name         TEXT NOT NULL,
    bank_name            TEXT NOT NULL,
    bank_code            TEXT,
    is_active            BOOLEAN NOT NULL DEFAULT true,
    metadata             JSONB,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX virtual_accounts_user_id_idx ON virtual_accounts (user_id);
CREATE INDEX virtual_accounts_account_reference_idx ON virtual_accounts (account_reference);
CREATE INDEX virtual_accounts_account_number_idx ON virtual_accounts (account_number);

-- ─── webhook idempotency ────────────────────────────────────────────────────

CREATE TABLE webhook_events (
    id             UUID PRIMARY KEY,
    provider       TEXT NOT NULL,
    external_id    TEXT NOT NULL,
    payload_hash   TEXT NOT NULL,
    received_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at   TIMESTAMPTZ,
    status         TEXT NOT NULL DEFAULT 'RECEIVED',
    UNIQUE (provider, external_id)
);
CREATE INDEX webhook_events_received_at_idx ON webhook_events (received_at);

-- ─── transactional outbox ───────────────────────────────────────────────────

CREATE TABLE outbox_events (
    id                UUID PRIMARY KEY,
    topic             TEXT NOT NULL,
    aggregate_id      TEXT NOT NULL,
    payload           JSONB NOT NULL,
    status            VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    attempts          INTEGER NOT NULL DEFAULT 0,
    last_error        TEXT,
    next_attempt_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX outbox_events_status_next_attempt_at_idx ON outbox_events (status, next_attempt_at);
CREATE INDEX outbox_events_topic_idx ON outbox_events (topic);

-- ─── audit log ──────────────────────────────────────────────────────────────

CREATE TABLE audit_logs (
    id           UUID PRIMARY KEY,
    user_id      UUID REFERENCES users (id) ON DELETE SET NULL,
    action       TEXT NOT NULL,
    ip           TEXT,
    user_agent   TEXT,
    metadata     JSONB,
    success      BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_logs_user_id_idx ON audit_logs (user_id);
CREATE INDEX audit_logs_action_idx ON audit_logs (action);
CREATE INDEX audit_logs_created_at_idx ON audit_logs (created_at);
