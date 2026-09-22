INSERT INTO fsm_flow_version(flow_key, version, status, definition)
VALUES ('order', 1, 'ACTIVE', $json$ {
  "initialState": "NEW",
        "table": {
    "autoTransitionEnabled": false,
        "maxImmediateAutoTransitions": 0,
        "transitions": {
      "NEW": [
        {
          "from": "NEW",
        "to": {
            "state": "IN_PROGRESS",
        "conditions": [],
        "actions": [],
        "postActions": [],
        "timeout": null
          },
        "event": "SUBMIT"
        }
      ],
        "IN_PROGRESS": [
        {
          "from": "IN_PROGRESS",
        "to": {
            "state": "COMPLETED",
        "conditions": [],
        "actions": [],
        "postActions": [],
        "timeout": null
          },
        "event": "FINISH"
        }
      ],
        "COMPLETED": []
    }
  }
}
$json$::jsonb);
