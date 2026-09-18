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

The JSON uses the existing core `FsmDto` format. Guards, actions and post-actions
are stored as Spring bean names. Deserialization reuses the actual registered beans,
including Spring proxies and injected dependencies. It does not serialize bean internals.
Unknown names fail restoration. Serialization requires each handler to be the actual
registered bean instance with exactly one registered name; unregistered lambdas and
ambiguous instances fail instead of losing behavior. Core `NamedAction` IDs do not
override Spring bean names. Changing a bean name requires migrating stored definitions.

States and events use `toString()` when saved; provide matching parsers when loading.
Handler context types must match the state and domain context of the table; JSON does
not carry Kotlin generic types. Handlers should keep per-domain state in the supplied
context rather than mutable singleton fields.

The starter does not create a shared mutable FSM, access a database or manage flow
versions. Create an FSM for each domain object using the restored table. Persistence,
initial state, scheduling and business validation remain application responsibilities.

```bash
./gradlew :fsm-spring-boot-starter:test
```
