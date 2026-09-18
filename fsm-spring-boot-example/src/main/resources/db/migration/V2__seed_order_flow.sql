INSERT INTO fsm_flow(flow_key)
VALUES ('order')
ON CONFLICT (flow_key) DO NOTHING;

INSERT INTO fsm_flow_version(flow_id, version, status, definition, published_at)
SELECT id, 1, 'ACTIVE', $json$
    {
      "schemaVersion": 1,
      "initialState": "NEW",
      "autoTransitionEnabled": false,
      "states": ["NEW", "PAYMENT_PENDING", "PAID", "SHIPPED", "COMPLETED", "CANCELLED"],
      "events": ["SUBMIT", "PAY", "SHIP", "DELIVER", "CANCEL"],
      "transitions": [
        {"id":"submit","from":"NEW","to":"PAYMENT_PENDING","trigger":{"kind":"event","event":"SUBMIT"}},
        {"id":"pay","from":"PAYMENT_PENDING","to":"PAID","trigger":{"kind":"event","event":"PAY"},"guards":["paymentApproved"],"actions":["capturePayment"],"postActions":["sendPaymentReceipt"]},
        {"id":"ship","from":"PAID","to":"SHIPPED","trigger":{"kind":"event","event":"SHIP"}},
        {"id":"deliver","from":"SHIPPED","to":"COMPLETED","trigger":{"kind":"event","event":"DELIVER"}},
        {"id":"cancel-new","from":"NEW","to":"CANCELLED","trigger":{"kind":"event","event":"CANCEL"}},
        {"id":"cancel-pending","from":"PAYMENT_PENDING","to":"CANCELLED","trigger":{"kind":"event","event":"CANCEL"}}
      ]
    }
    $json$::jsonb, now()
FROM fsm_flow
WHERE flow_key = 'order'
ON CONFLICT (flow_id, version) DO UPDATE SET definition = EXCLUDED.definition;

UPDATE fsm_flow
SET active_version_id = (
    SELECT v.id
    FROM fsm_flow_version v
    WHERE v.flow_id = fsm_flow.id AND v.version = 1
)
WHERE flow_key = 'order';
