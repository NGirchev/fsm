# FSM Spring Boot Starter

Add `io.github.ngirchev:fsm-spring-boot-starter` using the same version as `fsm`.
The starter includes the core library and targets Spring Boot 3.5 / Java 17+.
For this checkout use `implementation(project(":fsm-spring-boot-starter"))`.

Boot automatically registers `FsmBeanRegistry` and `SpringFsmJsonSerializer`.
It also registers `FlowLoader`, and registers `FlowService` when the application provides a `FlowStore` bean.
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

## Versioned flows

`FlowService` owns version numbering, draft editing/deletion, definition validation and publication.
Stores supporting deletion implement `deleteDraft`: physically remove the draft, including from
`latest`. Deleting the highest draft permits its number to be reused. The default implementation
rejects deletion for stores that have not opted in.
`FlowDefinition.editor` optionally carries visual editor metadata. It is stored with the definition
and ignored by `FlowLoader`; definitions without this field remain supported. Stores that persist
definitions as JSON should retain this field when reading and writing versions.
Applications implement `FlowStore` to lock a flow key, read versions and persist changes. The
service runs mutations in a transaction; the store must hold its lock until that transaction ends
and persist status changes in order. Failed validation leaves the previous version active. The
starter does not choose a database, schema or locking strategy. The
[example](../fsm-spring-boot-example/README.md) implements the store with JPA and PostgreSQL.

Create an FSM for each domain object using the restored table. Domain state storage and business
validation remain application responsibilities.

## Optional administration panel

The same starter includes a ready-made version API, administration page and the universal visual
editor. They are **disabled by default**. The application must already use Spring MVC (for example,
`spring-boot-starter-web`); the FSM starter does not transitively install a web server or Spring Security.
This integration targets servlet applications, not WebFlux.

```yaml
fsm:
  admin:
    enabled: true
    base-path: /fsm-admin
```

Open `/fsm-admin/` under the application's context path. Changing these settings requires a restart.
The prefix must consist of slash-separated letters, digits, underscores or hyphens, without a trailing
slash. Disabling admin removes the controllers and resource mappings. Assets are stored outside
Spring Boot's automatic static locations. The published JAR already includes the built editor;
consumers do not need Node.js. Building this repository's starter from source requires Node.js 22+ and npm.

Provide the existing `FlowStore` and transaction manager, and one `FsmAdminRegistration` bean for
each editable flow. An enabled panel without a store fails startup. Duplicate or invalid keys also
fail startup. An empty registration list shows an empty panel. Unregistered keys return 404, even
if the store contains their definitions.

```java
@Bean
FsmAdminRegistration orderAdmin(OrderFlowValidator validator) {
    var initial = new FlowDefinition("NEW", new FsmDto(false, Map.of("NEW", List.of())));
    return new FsmAdminRegistration("order", "Orders", initial,
            OrderEvent::validateFlowEvents, validator::validate);
}
```

The starter discovers Spring beans by interface: `Guard<?>`, `Action<?>`, `StateChangeListener<?>`
and `Consumer<?>` (completion listeners). Bean names are the IDs stored in definitions; `@Description`
provides editor labels, falling back to the bean name. Every registered flow sees the same catalog.
Applications do not supply a catalog or create handler descriptors. The HTTP response retains
`id`, `kind` (`guard`, `action`, `stateListener`, `completionListener`) and `description` for the editor.

The example's `OrderFlowValidator` and `OrderEvent` above are application-owned. Admin checks that
each name exists under the required bean interface before publication/activation. Domain compatibility
is the application's responsibility: for example, `OrderFlowValidator` checks guards/actions against
the application's injected `Map<String, Guard<Order>>` / `Map<String, Action<Order>>`. The starter
does not know `Order` or filter the catalog by a flow's domain type. Applications must also check
listener compatibility; a shared state type alone does not establish domain compatibility.
The draft callback checks create/update requests; both callbacks run before publication and
reactivation. Activation validation and the lifecycle change share a transaction and the flow-key
lock. Callbacks must not mutate their arguments. Storage and runtime invariants should also remain
enforced by the application's store/runtime when callers bypass the admin API.

The panel supports first drafts from the registration template, subsequent drafts from the selected
version, saving, deletion, publication and reactivation. Layout and descriptions persist in the same
definition. The editor retains its `fsm-editor/v1` iframe protocol and standalone export capabilities.

All endpoints are below the configured prefix:

| Method           | Suffix                                         | Purpose                                                            |
| ---------------- | ---------------------------------------------- | ------------------------------------------------------------------ |
| GET              | `/api/flows`                                   | Registered keys and titles                                         |
| GET              | `/api/flows/{key}/behaviors`                   | Spring bean catalog (all registered flows share it)                |
| GET, POST        | `/api/flows/{key}/versions`                    | List versions / create a draft; absent POST body uses the template |
| GET, PUT, DELETE | `/api/flows/{key}/versions/{version}`          | Read, update or delete a draft                                     |
| POST             | `/api/flows/{key}/versions/{version}/publish`  | Publish a draft                                                    |
| POST             | `/api/flows/{key}/versions/{version}/activate` | Reactivate an archived version                                     |
| GET              | `/api/csrf`                                    | Current CSRF header name and token, or `{}` when CSRF is absent    |

Successful creation returns 201, deletion 204. Invalid input returns 400, missing registrations or
versions 404, and invalid lifecycle operations 409, using `{ "message": "..." }` errors.
Error handling is scoped to the admin controllers.

### Application-owned security

Enabling admin does **not** secure it. The starter creates no `SecurityFilterChain`, users, roles,
login form or CORS policy. Add rules to the application's existing Spring Security configuration,
before more general matchers, covering both the exact prefix and everything below it:

```java
// Call from the application's existing SecurityFilterChain factory, before http.build().
void configureAdminAccess(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/fsm-admin", "/fsm-admin/**").hasRole("FSM_ADMIN")
            // Existing application rules follow here.
            .anyRequest().authenticated());
    http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
}
```

The application's filter chain must match these requests, including UI and editor resources; a chain
restricted to `/api/**` does not protect `/fsm-admin/**`. Adjust the rules when changing `base-path`.
Keep the application's authentication mechanism. Same-origin framing is needed for the packaged
editor, and for embedding the whole panel in an application page. An existing CSP must also permit
the required same-origin scripts, styles and frames. Configure those policies in the host, not by
disabling Spring Security headers globally.

The panel uses the application's session and fetches a fresh CSRF token before each mutation,
sending the returned header. The token endpoint is non-cacheable. Missing or denied token requests
stop the operation and preserve the dirty draft. CSRF is never disabled by the starter. Bearer-token
acquisition and a separate login UI are not provided. For an application page, embed
`<iframe src="./fsm-admin/" title="FSM administration"></iframe>` or link to the panel.

```bash
./gradlew :fsm-spring-boot-starter:test
```
