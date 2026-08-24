# Dynamic FSM Spring Boot Example

This module demonstrates a runtime FSM loaded from versioned PostgreSQL `JSONB` configuration.
States and events are strings, while guards and actions are resolved by their Spring bean names.

## Run

```bash
docker compose -f fsm-spring-boot-example/compose.yml up -d
./gradlew :fsm-spring-boot-example:bootRun
```

The Flyway seed creates active flow `order` version `1`.

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
