# FSM Spring Boot Starter

Add `io.github.ngirchev:fsm-spring-boot-starter` using the same version as `fsm`.
The starter includes the core library and targets Spring Boot 3.5 / Java 17+.
For this checkout use `implementation(project(":fsm-spring-boot-starter"))`.

Boot automatically registers `FsmBeanRegistry` and `SpringFsmJsonSerializer`.
No component scan or explicit import of the starter package is needed.
Application-defined beans of these types replace the defaults. The serializer uses
the application's Jackson `ObjectMapper`.

Declare handlers using the core interfaces:

```kotlin
@Bean
fun capturePayment(): Action<StateContext<String>> = Action { context ->
    (context as Order).paymentCaptured = true
}
```

Inject `SpringFsmJsonSerializer` to save and restore transition tables:

```kotlin
val json = serializer.serialize(table)
val restored = serializer.deserialize(json, { it }, { it })
val restoredFromDto = serializer.fromDto(dto, { it }, { it })
```

The JSON uses the existing core `FsmDto` format and preserves local `.auto()` settings.
Guards, actions and post-actions are stored as Spring bean names. Deserialization reuses the actual registered beans,
including Spring proxies and injected dependencies. It does not serialize bean internals.
Unknown names fail restoration. Serialization requires each handler to be the actual
registered bean instance with exactly one registered name; unregistered lambdas and
ambiguous instances fail instead of losing behavior. Core `NamedAction` IDs do not
override Spring bean names. Changing a bean name requires migrating stored definitions.

States and events use `toString()` when saved; provide matching parsers when loading.
Handler context types must match the state and domain context of the table; JSON does
not carry Kotlin generic types. Handlers should keep per-domain state in the supplied
context rather than mutable singleton fields.

## Durable tasks

`FsmTaskProcessor` provides the database-independent task lifecycle. When an application declares
one or more processor beans, the starter automatically creates a background worker. On each poll,
the worker calls `processNext()` until that processor's queue is empty or its per-poll limit is
reached. Every call starts a new Spring transaction, claims one pending task from an `FsmTaskStore`,
delegates it to an `FsmTaskHandler`, and completes it in that transaction. An exception from the
handler skips completion, rolls the transaction back, and leaves that processor for the next poll.

Applications provide the task type and store implementation. The store contract requires an
exclusive claim for the duration of the transaction but does not prescribe JPA, SQL, table names,
or a database dialect. For example, PostgreSQL implementations can use `FOR UPDATE SKIP LOCKED`,
while other databases can use their own locking or optimistic-claim strategy. Multiple application
instances can run workers when the store implements this claim contract correctly.

Worker settings use the `fsm.tasks` prefix:

```yaml
fsm:
  tasks:
    enabled: true
    poll-interval: 5s
    max-tasks-per-processor-per-poll: 100
```

Set `fsm.tasks.enabled=false` when processing is owned by an external job runner. The worker uses a
dedicated single-threaded `fsmTaskScheduler`; applications can replace that named `TaskScheduler`
bean when they need different execution infrastructure.

The starter does not create a shared mutable FSM, access a database or manage flow versions. Create
an FSM for each domain object using the restored table. Persistence, initial state, task storage and
business validation remain application responsibilities.

```bash
./gradlew :fsm-spring-boot-starter:test
```
