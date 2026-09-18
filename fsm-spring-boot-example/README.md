# Dynamic FSM Spring Boot Example

This module demonstrates a runtime FSM loaded from versioned PostgreSQL `JSONB` configuration.
States and events are strings, while guards and actions are resolved by their Spring bean names.

## Run

```bash
docker compose -f fsm-spring-boot-example/compose.yml up -d
./gradlew :fsm-spring-boot-example:bootRun
```

The Flyway seed creates active flow `order` version `1`.
Each order records the flow version it was created with, so publishing a new version does not break in-progress orders.

```bash
curl -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"totalAmount":42.00}'

curl -X POST http://localhost:8080/api/orders/1/events \
  -H 'Content-Type: application/json' \
  -d '{"event":"SUBMIT"}'
```

Flow management endpoints are under `/api/flows/{flowKey}/versions`. A draft is editable until
`POST /api/flows/{flowKey}/versions/{version}/publish` validates it and atomically makes it active.

## JSON format

A definition contains `initialState` and `table`. The table is the standard core `FsmDto` JSON,
produced by `toJson()` / `FsmJsonSerializer`; no separate transition schema is used:

```json
{
  "initialState": "NEW",
  "table": {
    "autoTransitionEnabled": false,
    "maxImmediateAutoTransitions": 0,
    "transitions": {
      "NEW": [{
        "from": "NEW",
        "event": "SUBMIT",
        "to": {
          "state": "PAYMENT_PENDING",
          "conditions": [],
          "actions": [],
          "postActions": [],
          "timeout": null
        }
      }]
    }
  }
}
```

`FlowLoader` delegates restoration to the core `toExTransitionTable()` converter. Its two factories
resolve ordinary `Action` and `Guard` Spring beans by name and retain those names for serialization.
Unknown beans reject publication rather than silently removing behavior.

Automatic execution requires a positive `table.maxImmediateAutoTransitions`, for both cyclic and
acyclic flows. This simple bound replaces example-specific graph analysis; the core runtime enforces
it. Active definitions are checked during startup. When upgrading an existing automatically executed
flow with an unlimited chain, configure a positive limit before restarting with this version.

Previously stored definitions (including the Flyway seed) remain readable through a small read-only
adapter. New drafts use the core format; no migration rewrites existing definitions or published versions.
