CREATE TABLE fsm_flow_version (
    id BIGSERIAL PRIMARY KEY,
    flow_key VARCHAR(120) NOT NULL,
    version INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    definition JSONB NOT NULL,
    UNIQUE (flow_key, version)
);

CREATE UNIQUE INDEX ux_fsm_flow_one_active_version
    ON fsm_flow_version(flow_key) WHERE status = 'ACTIVE';

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    state VARCHAR(120) NOT NULL,
    flow_version INTEGER NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    commission NUMERIC(19,2) NOT NULL DEFAULT 0.00
);

CREATE TABLE order_history (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    flow_version INTEGER NOT NULL,
    kind VARCHAR(32) NOT NULL,
    from_state VARCHAR(120) NOT NULL,
    to_state VARCHAR(120) NOT NULL,
    event VARCHAR(120),
    source VARCHAR(120),
    request_id VARCHAR(120)
);
CREATE INDEX ix_order_history_order ON order_history(order_id, id);

INSERT INTO fsm_flow_version(flow_key, version, status, definition)
VALUES ('order', 1, 'ARCHIVED', $direct$ {
  "initialState": "NEW",
  "editor": {
    "name": "Fixed commission",
    "states": [
      { "id": "direct-new", "label": "NEW", "position": { "x": 0, "y": 100 }, "color": "#6d28d9" },
      { "id": "direct-progress", "label": "IN_PROGRESS", "position": { "x": 260, "y": 100 }, "color": "#0f766e" },
      { "id": "direct-completed", "label": "COMPLETED", "position": { "x": 520, "y": 100 }, "color": "#9a3412" }
    ]
  },
  "execution": { "stateListeners": ["recordOrderHistory"], "completionListeners": [] },
  "table": {
    "autoTransitionEnabled": false,
    "maxImmediateAutoTransitions": 0,
    "transitions": {
      "NEW": [{ "from": "NEW", "event": "SUBMIT", "to": { "state": "IN_PROGRESS", "conditions": [], "actions": [], "postActions": [], "timeout": null } }],
      "IN_PROGRESS": [{ "from": "IN_PROGRESS", "event": "FINISH", "to": { "state": "COMPLETED", "conditions": [], "actions": ["commissionTwoPercent"], "postActions": ["logOrderNotification"], "timeout": null } }],
      "COMPLETED": []
    }
  }
} $direct$::jsonb),
('order', 2, 'ACTIVE', $json$ {
  "initialState": "NEW",
  "editor": {
    "name": "Commission by amount",
    "states": [
      { "id": "branch-new", "label": "NEW", "position": { "x": 0, "y": 180 }, "color": "#6d28d9" },
      { "id": "branch-progress", "label": "IN_PROGRESS", "position": { "x": 260, "y": 180 }, "color": "#0f766e" },
      { "id": "branch-two", "label": "COMMISSION_2_PERCENT", "position": { "x": 550, "y": 40 }, "color": "#0369a1" },
      { "id": "branch-one", "label": "COMMISSION_1_PERCENT", "position": { "x": 550, "y": 320 }, "color": "#a21caf" },
      { "id": "branch-completed", "label": "COMPLETED", "position": { "x": 860, "y": 180 }, "color": "#9a3412" }
    ]
  },
  "execution": { "stateListeners": ["recordOrderHistory"], "completionListeners": [] },
  "table": {
    "autoTransitionEnabled": false,
    "maxImmediateAutoTransitions": 0,
    "transitions": {
      "NEW": [{ "from": "NEW", "event": "SUBMIT", "to": { "state": "IN_PROGRESS", "conditions": [], "actions": [], "postActions": [], "timeout": null } }],
      "IN_PROGRESS": [
        { "from": "IN_PROGRESS", "event": "FINISH", "to": { "state": "COMMISSION_2_PERCENT", "conditions": ["amountBelowCommissionThreshold"], "actions": ["commissionTwoPercent"], "postActions": [], "timeout": null } },
        { "from": "IN_PROGRESS", "event": "FINISH", "to": { "state": "COMMISSION_1_PERCENT", "conditions": ["amountAtLeastCommissionThreshold"], "actions": ["commissionOnePercent"], "postActions": [], "timeout": null } }
      ],
      "COMMISSION_2_PERCENT": [{ "from": "COMMISSION_2_PERCENT", "event": null, "to": { "state": "COMPLETED", "conditions": [], "actions": [], "postActions": ["logOrderNotification"], "timeout": null, "autoTransitionEnabled": true } }],
      "COMMISSION_1_PERCENT": [{ "from": "COMMISSION_1_PERCENT", "event": null, "to": { "state": "COMPLETED", "conditions": [], "actions": [], "postActions": ["logOrderNotification"], "timeout": null, "autoTransitionEnabled": true } }],
      "COMPLETED": []
    }
  }
} $json$::jsonb);
