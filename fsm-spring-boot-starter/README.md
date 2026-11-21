# FSM Spring Boot Starter

Add `io.github.ngirchev:fsm-spring-boot-starter` using the same version as `fsm`.
The starter includes the core library and targets Spring Boot 4.1 / Jackson 3 / Java 17+.
For this checkout use `implementation(project(":fsm-spring-boot-starter"))`.

Boot automatically registers `FsmBeanRegistry` and `SpringFsmJsonSerializer`.
It also registers `FlowLoader`, and registers `FlowService` when the application provides a `FlowStore` bean.
No component scan or explicit import of the starter package is needed.
Application-defined beans of these types replace the defaults. The serializer uses
the application's Jackson 3 `JsonMapper` (`tools.jackson`); the starter adds Jackson 3's Kotlin module
that reads the core DTOs.

The core `fsm` module still brings Jackson 2 (`com.fasterxml.jackson`) for its own `FsmJsonSerializer`.
Hibernate prefers Jackson 2 when both are present, so an application that stores `FlowDefinition` or
its `editor` node in a JSON column sets
`spring.jpa.properties.hibernate.type.json_format_mapper=org.hibernate.type.format.jackson.Jackson3JsonFormatMapper`.

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
handler, including a checked exception, skips completion, rolls the transaction back, and leaves
that processor for the next poll. The worker continues processing the other processors.

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
rejects deletion for stores that have not opted in. `deleteFlow` follows the same pattern for a whole
flow: the store removes every version of the key, the active one included, and the default
implementation rejects the call. `FlowService.deleteFlow` runs it under the flow-key lock and leaves
the decision whether a flow may be deleted to the caller.

`FlowDefinition.finalStates` optionally maps a state to the outcome of a run that ends there:
`SUCCESS`, `FAILURE` or `LIMIT` (`FinalStateOutcome`). The starter does not interpret the outcome; the
application reads it after a run. `FlowLoader` requires every listed state to be a source or target
in the table and to have no outgoing transitions. It does not require any final state: an application
that needs one checks `definition.finalStates()` in its own validation, for example the
`validateActivation` callback of the administration API.

A definition carries two limits. `table.maxImmediateAutoTransitions` bounds a chain of automatic
transitions; `0`, which is also the value of a definition that omits the property, means the limit is
not stated. The core then runs the chain without a limit, so an application with its own default
replaces `0` before it builds the table, and a negative value is rejected.
`execution.maxIterations` is a number the application interprets (for example the iterations of an
agent loop); `null` or absent means the application default, and a stated value lower than 1 is
rejected. Both checks are part of `FlowLoader`, so they apply to publication, reactivation and loading.

```json
{
  "initialState": "NEW",
  "table": { "autoTransitionEnabled": true, "maxImmediateAutoTransitions": 50, "transitions": { "NEW": [], "DONE": [] } },
  "execution": { "stateListeners": [], "completionListeners": [], "maxIterations": 12 },
  "finalStates": { "DONE": "SUCCESS" }
}
```

`finalStates` is written only when it is not empty and `maxIterations` only when it is stated, so
definitions stored before these properties existed read and write unchanged.

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

## Optional administration API

The same starter includes a version management API for the separately deployed
[visual editor](../fsm-visual-editor/README.md#connecting-to-a-backend). It is **disabled by default**. The application must already use Spring MVC (for example,
`spring-boot-starter-web`); the FSM starter does not transitively install a web server or Spring Security.
This integration targets servlet applications, not WebFlux.

```yaml
fsm:
  admin:
    enabled: true
    base-path: /fsm-admin
```

The starter serves no pages or static assets. Connect an editor to the API's base URL, for example
`https://fsm-editor.example.com/?backend=https://orders.example.com/fsm-admin` (include the
application's context path). Changing these settings requires a restart. The prefix must consist of
slash-separated letters, digits, underscores or hyphens, without a trailing slash. Disabling admin
removes the controllers.

The starter adds no CORS policy. When the editor runs on another origin, the application allows it
like any other client. The editor sends cookies, so credentials must be allowed, which requires
exact origins rather than `*`:

```java
@Bean
WebMvcConfigurer fsmEditorCors() {
    return new WebMvcConfigurer() {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/fsm-admin/api/**").allowedOrigins("https://fsm-editor.example.com")
                    .allowedMethods("GET", "POST", "PUT", "DELETE").allowCredentials(true);
        }
    };
}
```

Provide the existing `FlowStore` and transaction manager, and one `FsmAdminRegistration` bean for
each editable flow (or an `FsmAdminFlows` bean, see below). An enabled API without a store fails
startup. Duplicate or invalid registration keys also fail startup. An empty registration list returns
no flows. Unregistered keys return 404, even if the store contains their definitions.

```java
@Bean
FsmAdminRegistration orderAdmin(OrderFlowValidator validator) {
    var initial = new FlowDefinition("NEW", new FsmDto(false, Map.of("NEW", List.of())));
    return new FsmAdminRegistration("order", "Orders", initial,
            OrderEvent::validateFlowEvents, validator::validate);
}
```

A registration optionally states a `kind` (the group the editor shows the flow in, `other` by
default), a `description` and whether the editor should offer deletion (`deletable`, `false` by
default): `new FsmAdminRegistration(key, title, initial, validateDraft, validateActivation, "agent",
"Plans first, then acts", true)`.

### Flows created at run time

The API reads its flows from an `FsmAdminFlows` bean on every request. The default bean serves the
`FsmAdminRegistration` beans and refuses to create, rename or delete a flow (405). An application
that keeps flows in its own storage declares its own bean; the default one then backs off and the
`FsmAdminRegistration` beans are not used:

```java
@Bean
FsmAdminFlows adminFlows(AgentFlowRepository flows) {
    return new FsmAdminFlows() {
        public List<FsmAdminRegistration> all() { return flows.registrations(); }
        public FsmAdminRegistration find(String flowKey) { return flows.registration(flowKey); } // null when unknown
        public FsmAdminRegistration create(String flowKey, String title, String kind, String description, String copyOf) {
            if (!kind.equals("agent")) throw new IllegalArgumentException("Only agent flows can be created"); // 400
            return flows.insert(flowKey, title, kind, description);
        }
        public FsmAdminRegistration rename(String flowKey, String title, String description) {
            return flows.update(flowKey, title, description);
        }
        public void delete(String flowKey) {
            if (flows.isBuiltIn(flowKey)) throw new IllegalStateException("Built-in flows cannot be deleted"); // 409
            flows.remove(flowKey);
        }
    };
}
```

The three mutating methods are optional; one that is not overridden answers 405. Before `create`
the API checks the key (`[A-Za-z0-9._-]{1,120}`, not only dots), that title and kind are not blank and that the key
is not registered (409), and takes the flow-key lock. After `create` it stores the first draft: the
`initialDefinition` of the returned registration, or with `copyOf` version `copyVersion` of that flow
(404 if it is missing), else its active version, else its newest version. `DELETE` calls `FsmAdminFlows.delete` and then `FlowService.deleteFlow`, so
the store must implement `FlowStore.deleteFlow`. All of it runs in the administration transaction.
`fsmAdminService`, `fsmAdminController` and `fsmAdminExceptionHandler` are also replaceable by
application beans of the same types; a subclass of `FsmAdminService` can override `flow(registration)`
to add properties to the flow JSON.

### Bean catalog

The starter discovers Spring beans by interface: `Guard<?>`, `Action<?>`, `StateChangeListener<?>`
and `Consumer<?>` (completion listeners). Bean names are the IDs stored in definitions; `@Description`
provides editor labels, falling back to the bean name. Applications do not supply a catalog or create
handler descriptors. Every flow receives the whole catalog; each entry names the kind of flow its bean
works with, so the editor can filter:

```java
@Bean
@FsmFlowKind("agent")
@Description("The model asked for a tool")
Guard<StateContext<String>> hasToolCall() { ... }
```

`@FsmFlowKind` goes on the `@Bean` method or on the bean class; beans without it are reported as
`other`. The HTTP response is `[{id, kind, flowKind, description}]` with `kind` one of `guard`,
`action`, `stateListener`, `completionListener`. The kind is a hint for the editor only: publication
accepts a bean of any flow kind, and the application decides what such a bean does at run time.

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

The API supports flow creation, renaming and deletion when the application allows them, first drafts
from the registration template, subsequent drafts from a submitted definition, saving, deletion,
publication and reactivation. Layout and descriptions persist in the same
definition.

All endpoints are below the configured prefix:

| Method           | Suffix                                         | Purpose                                                            |
| ---------------- | ---------------------------------------------- | ------------------------------------------------------------------ |
| GET              | `/api/flows`                                   | `[{flowKey, title, kind, description, deletable}]`                 |
| POST             | `/api/flows`                                   | Create a flow from `{flowKey, title, kind, description?, copyOf?, copyVersion?}` |
| PUT, DELETE      | `/api/flows/{key}`                             | Rename with `{title, description?}` / delete with all versions     |
| GET              | `/api/flows/{key}/behaviors`                   | Spring bean catalog with the flow kind of every bean               |
| GET, POST        | `/api/flows/{key}/versions`                    | List versions / create a draft; absent POST body uses the template |
| GET, PUT, DELETE | `/api/flows/{key}/versions/{version}`          | Read, update or delete a draft                                     |
| POST             | `/api/flows/{key}/versions/{version}/publish`  | Publish a draft                                                    |
| POST             | `/api/flows/{key}/versions/{version}/activate` | Reactivate an archived version                                     |

Successful creation returns 201, deletion 204. Invalid input returns 400, missing registrations or
versions 404, invalid lifecycle operations 409, and an operation the store or the `FsmAdminFlows`
bean does not implement 405, using `{ "message": "..." }` errors.
Error handling is scoped to the admin controllers.

### Application-owned security

Enabling admin does **not** secure it. The starter creates no `SecurityFilterChain`, users, roles
or login form. Add rules to the application's existing Spring Security configuration, before more
general matchers, covering both the exact prefix and everything below it:

```java
// Call from the application's existing SecurityFilterChain factory, before http.build().
void configureAdminAccess(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/fsm-admin", "/fsm-admin/**").hasRole("FSM_ADMIN")
            // Existing application rules follow here.
            .anyRequest().authenticated());
    // Only for a cross-origin editor: applies the MVC CORS mapping before authentication.
    http.cors(Customizer.withDefaults());
}
```

A chain restricted to `/api/**` does not protect `/fsm-admin/**`. Adjust the rules when changing
`base-path`. Keep the application's authentication mechanism.

CSRF protection is also the application's: the starter exposes no token endpoint and never disables
CSRF. With Spring Security's default session CSRF, a POST, PUT or DELETE without a token is rejected
with 403. Either exempt `/fsm-admin/api/**` when it is authenticated by tokens rather than cookies,
or make a token available and add it in the editor's request function. A failed request keeps the
editor's unsaved draft.

The editor sends cookies with every request. Browsers send a session cookie to another origin only when the cookie allows it: an editor on a
subdomain of the same site works with the default `SameSite=Lax`; an editor on a different site
needs `SameSite=None; Secure` cookies or a token. Token acquisition and login UI are not provided;
a deployment can replace the editor's request function to add its own credentials
(see [editor configuration](../fsm-visual-editor/README.md#connecting-to-a-backend)).

```bash
./gradlew :fsm-spring-boot-starter:test
```
