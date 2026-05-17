# FSM Visual Editor

Local visual finite state machine editor for building `.fsm.json` projects and generating Java/Kotlin factory code compatible with the Kotlin `fsm` library.

## Run

```bash
npm install
npm run dev
```

## What The JSON Stores

The editor JSON is the source of truth for the UI. It stores FSM states, event IDs, transitions, canvas positions, reusable guard/action IDs, and Java/Kotlin generation metadata.

Use `.fsm.json` as the single project format for import, export, autosave, and continued editing.

Transitions have an explicit trigger:

- `event` transitions generate `.onEvent(EventEnum.X)`;
- `auto` transitions intentionally generate no `.onEvent(...)` and rely on the FSM library's auto-transition behavior.

## Projects

When running through `npm run dev`, the local Node/Vite server autosaves the current editor JSON into `projects/*.fsm.json`.

Saved files appear in the Recent dropdown and can be reopened later. The same document is also mirrored to browser local storage, so editing can continue even if the projects API is unavailable.

## Java And Kotlin Generation

The generated Java/Kotlin class is self-contained: it includes the state enum, event enum, a domain DTO implementing `StateContext`, reusable guard/action placeholders, and a factory method returning:

```java
ExDomainFsm<DomainType, StateType, EventType>
```

Guard and action IDs are emitted as class-level lambda fields. The generated lambdas are placeholders and are intended to be filled in or wired to real domain behavior.
