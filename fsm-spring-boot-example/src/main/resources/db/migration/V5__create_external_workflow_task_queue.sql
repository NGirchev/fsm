CREATE TABLE external_workflow_task (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES external_workflow(id),
    expected_version BIGINT NOT NULL,
    task_type VARCHAR(100) NOT NULL,
    expected_state VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_external_workflow_task_transition
        UNIQUE (workflow_id, task_type, expected_state, expected_version)
);

CREATE INDEX ix_external_workflow_task_pending
    ON external_workflow_task (id)
    WHERE status = 'PENDING';
