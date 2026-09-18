CREATE TABLE fsm_flow (
    id BIGSERIAL PRIMARY KEY,
    flow_key VARCHAR(120) NOT NULL UNIQUE,
    active_version_id BIGINT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fsm_flow_version (
    id BIGSERIAL PRIMARY KEY,
    flow_id BIGINT NOT NULL REFERENCES fsm_flow(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    definition JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ NULL,
    UNIQUE (flow_id, version)
);

ALTER TABLE fsm_flow
    ADD CONSTRAINT fk_fsm_flow_active_version
    FOREIGN KEY (active_version_id) REFERENCES fsm_flow_version(id);

CREATE UNIQUE INDEX ux_fsm_flow_one_active_version
    ON fsm_flow_version(flow_id) WHERE status = 'ACTIVE';

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    state VARCHAR(120) NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    payment_captured BOOLEAN NOT NULL DEFAULT FALSE,
    receipt_sent BOOLEAN NOT NULL DEFAULT FALSE,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
