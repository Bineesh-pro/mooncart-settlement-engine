CREATE TABLE reconciliation_runs (
    id              UUID PRIMARY KEY,
    period_start    DATE         NOT NULL,
    period_end      DATE         NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    started_at      TIMESTAMP WITH TIME ZONE,
    completed_at    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE match_groups (
    id                      UUID PRIMARY KEY,
    reconciliation_run_id   UUID         NOT NULL REFERENCES reconciliation_runs(id),
    confidence_score        NUMERIC(5, 2),
    match_method            VARCHAR(32),
    amount_variance_pct     NUMERIC(10, 6),
    settlement_delay_days   INTEGER,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE yuno_transactions (
    id                      UUID PRIMARY KEY,
    yuno_transaction_id     VARCHAR(128) NOT NULL,
    timestamp               TIMESTAMP WITH TIME ZONE NOT NULL,
    amount                  NUMERIC(19, 4) NOT NULL,
    currency                VARCHAR(3)   NOT NULL,
    status                  VARCHAR(32)  NOT NULL,
    merchant_id             VARCHAR(64)  NOT NULL,
    customer_email          VARCHAR(255) NOT NULL,
    order_reference         VARCHAR(128) NOT NULL,
    reconciliation_run_id   UUID         NOT NULL REFERENCES reconciliation_runs(id),
    match_group_id          UUID         REFERENCES match_groups(id),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_yuno_tx_run UNIQUE (reconciliation_run_id, yuno_transaction_id)
);

CREATE TABLE bank_settlements (
    id                      UUID PRIMARY KEY,
    bank_reference_number   VARCHAR(128) NOT NULL,
    settlement_date         DATE         NOT NULL,
    settled_amount          NUMERIC(19, 4) NOT NULL,
    currency                VARCHAR(3)   NOT NULL,
    yuno_transaction_id     VARCHAR(128),
    reconciliation_run_id   UUID         NOT NULL REFERENCES reconciliation_runs(id),
    match_group_id          UUID         REFERENCES match_groups(id),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_bank_settlement_run UNIQUE (reconciliation_run_id, bank_reference_number)
);

CREATE TABLE internal_orders (
    id                      UUID PRIMARY KEY,
    order_id                VARCHAR(128) NOT NULL,
    customer_email          VARCHAR(255) NOT NULL,
    order_amount            NUMERIC(19, 4) NOT NULL,
    currency                VARCHAR(3)   NOT NULL,
    timestamp               TIMESTAMP WITH TIME ZONE NOT NULL,
    payment_status          VARCHAR(32)  NOT NULL,
    reconciliation_run_id   UUID         NOT NULL REFERENCES reconciliation_runs(id),
    match_group_id          UUID         REFERENCES match_groups(id),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_internal_order_run UNIQUE (reconciliation_run_id, order_id)
);

CREATE TABLE discrepancies (
    id                      UUID PRIMARY KEY,
    reconciliation_run_id   UUID         NOT NULL REFERENCES reconciliation_runs(id),
    match_group_id          UUID         REFERENCES match_groups(id),
    type                    VARCHAR(64)  NOT NULL,
    severity                VARCHAR(16)  NOT NULL,
    source_entity_type      VARCHAR(32)  NOT NULL,
    source_entity_id        UUID         NOT NULL,
    currency                VARCHAR(3),
    amount                  NUMERIC(19, 4),
    merchant_id             VARCHAR(64),
    details                 JSON,
    investigation_priority  NUMERIC(10, 4),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_yuno_run_match ON yuno_transactions (reconciliation_run_id, match_group_id);
CREATE INDEX idx_yuno_order_ref ON yuno_transactions (order_reference);
CREATE INDEX idx_yuno_email_currency_ts ON yuno_transactions (customer_email, currency, timestamp);
CREATE INDEX idx_yuno_merchant ON yuno_transactions (merchant_id);

CREATE INDEX idx_bank_run_match ON bank_settlements (reconciliation_run_id, match_group_id);
CREATE INDEX idx_bank_yuno_tx_id ON bank_settlements (yuno_transaction_id);

CREATE INDEX idx_order_run_match ON internal_orders (reconciliation_run_id, match_group_id);
CREATE INDEX idx_order_email_currency_ts ON internal_orders (customer_email, currency, timestamp);

CREATE INDEX idx_discrepancy_type_currency_created ON discrepancies (type, currency, created_at);
CREATE INDEX idx_discrepancy_run ON discrepancies (reconciliation_run_id);
CREATE INDEX idx_match_group_run ON match_groups (reconciliation_run_id);
