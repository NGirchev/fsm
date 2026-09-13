# Spring Boot Transactional FSM Example

This module is an executable example only. It does not apply Maven publishing or signing plugins and is not intended for Maven Central publication.

The example demonstrates this status flow:

```text
NEW
  -- START action calls ExternalServiceClient successfully -->
AWAITING_EXTERNAL_SERVICE_RESULT
  -- auto branch -->
EXTERNAL_SERVICE_DONE or EXTERNAL_SERVICE_FAILED
  -- auto notify action -->
NOTIFY
  -- auto -->
END
```

Each transition uses `PersistWorkflowStatusAction` as a `postAction`, so the new state is saved after the FSM changes the state. Auto transitions use `auto().deferWith(AfterCommitAutoTransitionScheduler)`, so every deferred auto transition runs after the previous transaction commits and opens its own `REQUIRES_NEW` transaction. That is what lets Envers keep every intermediate status in the audit history.

`ExternalWorkflow` uses optimistic versioning, while `start` locks the workflow row before invoking
the external service. The lock prevents concurrent starts from submitting the same workflow twice;
the version field also protects later detached-entity merges from lost updates.

Run the example tests:

```bash
./gradlew :fsm-spring-boot-transactional-example:test
```

Run the demo application:

```bash
./gradlew :fsm-spring-boot-transactional-example:bootRun
```
