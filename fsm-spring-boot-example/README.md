# Spring Boot FSM Example

This is the repository's single executable Spring Boot example. It demonstrates both a runtime FSM
loaded from versioned PostgreSQL `JSONB` configuration and a transactional JPA workflow whose
automatic transitions run after commit.

## Run

```bash
docker compose -f fsm-spring-boot-example/compose.yml up -d
./gradlew :fsm-spring-boot-example:bootRun
```

The Flyway seed creates active flow `order` version `1`.
Each order records the flow version it was created with, so publishing a new version does not break in-progress orders.
When automatic execution is enabled, creating an order runs automatic transitions from its initial
state and saves their result in the creation transaction. Exceeding the runtime limit rolls back
the order creation.

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

## Transactional workflow

The transactional scenario demonstrates this flow:

```text
NEW
  -- START action calls ExternalServiceClient successfully -->
AWAITING_EXTERNAL_SERVICE_RESULT
  -- queued ADVANCE task -->
EXTERNAL_SERVICE_DONE or EXTERNAL_SERVICE_FAILED
  -- queued ADVANCE task calls notification client -->
NOTIFY
  -- queued ADVANCE task -->
END
```

Each transition uses `PersistWorkflowStatusAction` as a `postAction`, so the new state is saved after
the FSM changes the state. The same transaction also inserts an `ADVANCE` task for the new status.
The starter's `FsmTaskProcessor` owns the transactional claim/handle/complete lifecycle. This
example supplies `ExternalWorkflowTaskRepository` as its PostgreSQL store: it claims one pending
task with `FOR UPDATE SKIP LOCKED`. The domain handler then locks the workflow and sends one FSM
event. The transition creates the next task when another step is required. Hibernate Envers
therefore records every intermediate status in a separate revision.

This is a database-backed transactional task queue: committed tasks survive process restarts, and a
failed action rolls back both the state change and task completion so another worker invocation can
retry it. The database constraint prevents duplicate transition tasks, while the expected source
state and aggregate version let the handler complete stale work without applying its transition.
Including the version in the key also supports workflows that revisit the same state. External side
effects are still at-least-once; the notification client receives a stable idempotency key and must
deduplicate successful retries.

`ExternalWorkflow` uses optimistic versioning, while `start` locks the workflow row before invoking
the external service. The lock prevents concurrent starts from submitting the same workflow twice;
the version field protects later detached-entity merges from lost updates.

The starter detects `ExternalWorkflowTaskProcessor` and runs its durable queue worker automatically.
The default poll interval is five seconds, and each transition enqueues the task that drives the next
step. Set `FSM_TASKS_ENABLED=false` to hand processing to an external job runner instead. To create
and start one demo workflow at application startup, set `FSM_DEMO_RUNNER_ENABLED=true` before
`bootRun`; the background worker will then continue it.

Run its focused integration test with:

```bash
./gradlew :fsm-spring-boot-example:test --tests '*ExternalWorkflowServiceTest'
```

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

The example depends on [fsm-spring-boot-starter](../fsm-spring-boot-starter/README.md).
`FlowLoader` delegates restoration to its `SpringFsmJsonSerializer`, which uses the core converter
and resolves ordinary `Action` and `Guard` Spring beans by name. Use this serializer to export
tables containing ordinary Spring beans; the core serializer handles identifiable handlers only.
Unknown beans reject publication rather than silently removing behavior.

Automatic execution requires a positive `table.maxImmediateAutoTransitions`, for both cyclic and
acyclic flows. This simple bound replaces example-specific graph analysis; the core runtime enforces
it. Active definitions are checked during startup. When upgrading an existing automatically executed
flow with an unlimited chain, configure a positive limit before restarting with this version.

Previously stored definitions (including the Flyway seed) remain readable through a small read-only
adapter. New drafts use the core format; no migration rewrites existing definitions or published versions.
