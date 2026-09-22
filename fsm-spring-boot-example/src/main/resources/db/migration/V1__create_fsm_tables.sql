CREATE TABLE fsm_flow_version (
    id BIGSERIAL PRIMARY KEY,
    flow_key VARCHAR(120) NOT NULL,
    version INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    definition JSONB NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (flow_key, version)
);

CREATE UNIQUE INDEX ux_fsm_flow_one_active_version
    ON fsm_flow_version(flow_key) WHERE status = 'ACTIVE';

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    state VARCHAR(120) NOT NULL,
    flow_version INTEGER NOT NULL,
    approved BOOLEAN NOT NULL DEFAULT TRUE,
    notification_sent BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE notification_workflow (
    id BIGSERIAL PRIMARY KEY,
    state VARCHAR(255) NOT NULL
);

CREATE INDEX ix_notification_workflow_pending ON notification_workflow (id) WHERE state = 'NOTIFY';
