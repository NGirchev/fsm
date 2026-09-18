ALTER TABLE orders
    ADD COLUMN flow_version INTEGER;

UPDATE orders o
SET flow_version = active_version.version
FROM fsm_flow flow
JOIN fsm_flow_version active_version ON active_version.id = flow.active_version_id
WHERE flow.flow_key = 'order';

ALTER TABLE orders
    ALTER COLUMN flow_version SET NOT NULL;
