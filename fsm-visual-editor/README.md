# FSM Visual Editor

Local visual finite state machine editor for building `.fsm.json` projects and generating Java/Kotlin factory code compatible with the Kotlin `fsm` library.

## Run

Run these commands from the repository root (`fsm`), including when using the Run button
on this code block in the IDE:

```bash
npm --prefix fsm-visual-editor install
npm --prefix fsm-visual-editor run dev
```

Open the local URL printed by Vite. This starts the standalone editor; the Spring example
and PostgreSQL are not required.

## Embedding the universal editor

State colors are assigned randomly from the 12-color palette on creation or import if missing.
Loading a saved diagram never randomizes colors. Select a state and use **State → Color** to change its color. Existing colors
remain fixed when other states are added or removed. The optional `states[].color` field stores the
palette's hex color in editor JSON and in the host's `editor.states` metadata; it does not affect FSM
execution or generated code. Save the document to retain assigned colors. Read-only flows also lock
the color selector. Event colors continue to be assigned automatically by event ID.

One static application runs on GitHub Pages, locally, or in an iframe. There is no domain-specific
entry point or build mode. The editor does not know about orders, flow versions, application APIs
or publication. The host application owns those operations, its catalogs and event type choices.

Use the deployed editor URL with `?parentOrigin=` set to the exact, URL-encoded host origin
(scheme, hostname and port; no trailing slash). Without that parameter the editor opens local
projects with Java/Kotlin export. Embedded documents never use the editor's local autosave API.
The default build uses relative asset URLs, so the same built directory can be served under any path.

The iframe contract uses `postMessage`, channel `fsm-editor/v1`:

| Direction     | Type               | Payload                                                                                                                                   |
| ------------- | ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------- |
| Editor → host | `ready`            | Host can now load a document. Also emitted after iframe reload.                                                                           |
| Host → editor | `load`             | Fresh string `session`, `document` (editor JSON) or `definition` (runtime JSON), boolean `readOnly`, optional `catalog`.                  |
| Editor → host | `loaded`, `change` | `session`, current `document`; the first response supplies the normalized baseline.                                                       |
| Host → editor | `configure`        | `session`, boolean `readOnly`; preserves selection and unsaved document.                                                                  |
| Host → editor | `snapshot`         | `session`, string `requestId`; freezes editing for a save.                                                                                |
| Editor → host | `snapshot`         | `session`, `requestId`, `document` and validated `definition`, or `error` if invalid.                                                     |
| Editor → host | `error`            | `session`, error text for an invalid load; the current document remains intact.                                                           |

Catalog entries are `{id, kind, description}` with kinds `guard`, `action`, `stateListener`,
`completionListener`.
Every message includes `channel`. Always verify both `event.source === iframe.contentWindow`
and the exact editor origin, and ignore replies for old sessions or unknown request IDs.
Use an explicit target origin, never `*`. The editor applies the equivalent checks to its parent.
After saving or an error, send `configure` to restore the appropriate read-only state. On timeout,
report the failure and unlock; do not claim the document was saved.

The [Spring starter admin panel](../fsm-spring-boot-starter/README.md#optional-administration-panel)
implements this contract and is embedded by the Spring example. The editor receives only documents
and generic configuration; its standalone build remains independent of the admin API.

## Browser regression tests

`npm run test:codegen` exports Java/Kotlin factories in both styles, compiles them against
the current core and executes local auto plus event transitions. It requires Java 17+ and uses
an isolated Gradle test project under `codegen-test`; generated files stay in its build directory.
`npm test` runs the frontend unit tests. The local auto flag and chain limit are editable in
standalone and embedded use. Runtime order journeys are covered by
[OrderFlowJourneyIT](../fsm-spring-boot-example/src/test/java/io/github/ngirchev/fsm/example/order/OrderFlowJourneyIT.java).

Requires Node 22+, Java 17+ and a running Docker daemon. From this directory:

```bash
npm ci
npx playwright install chromium
npm run test:e2e
```

Or run from the repository root (after installing Chromium once):

```bash
./gradlew playwrightTest
```

The task is listed under `fsm-spring-boot-example` → `verification` in the Gradle panel.
It builds the application and frontend dependencies before running the same isolated suite.
It is explicit and does not run as part of the ordinary `check` task.

The runner builds the example, starts a disposable PostgreSQL container and Spring application
under `/test`, and gives Vite a temporary projects directory. It never uses the development
database or `projects/`. Chromium runs headlessly with a fresh context per test. Servers,
container and temporary files are removed on completion or interruption. On failure, see
`playwright-report/`, `test-results/` (screenshots and traces) and `e2e-server.log`.
Pass Playwright options after `--`, for example `npm run test:e2e -- --grep 'delete draft'`.

| Scenarios                                                                                                                                  | Tests                           |
| ------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------- |
| Project settings, validation, add/rename/describe/drag/delete states, keyboard deletion and cascading edges                                | `e2e/graph.spec.ts`, both modes |
| Connect handles, duplicate connections, edge selection/deletion, endpoints, event/auto trigger, guards/actions/post-actions, timeout units | `e2e/graph.spec.ts`, both modes |
| Event and behavior creation/rename/deletion, reference propagation, JSON import/export and invalid input                                   | `e2e/graph.spec.ts`, both modes |
| Zoom, fit, minimap, interaction lock                                                                                                       | `e2e/graph.spec.ts`, both modes |
| Create/save/discard/delete/cancel, reload/layout persistence, read-only versions, failure/retry paths                                      | `e2e/order.spec.ts`             |
| Publish validation, new order version and unchanged existing orders, protected active/archive versions                                     | `e2e/order.spec.ts`             |
| Autosave, browser fallback, project switch/delete, failed project requests, Java/Kotlin exports in both styles                             | `e2e/projects.spec.ts`          |

Successful order operations use the real HTTP API and database. Failure scenarios deliberately
intercept only the request whose error handling is being tested. Unit tests remain `npm test`.

## GitHub Pages

The repository publishes this editor with the `GitHub Pages` workflow. The workflow builds the static Vite app from this directory and deploys `dist`.

Enable GitHub Pages in the repository settings with `GitHub Actions` as the source. Relative asset URLs work under the repository Pages path and when the same bundle is embedded elsewhere.

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

```text
ExDomainFsm<DomainType, StateType, EventType>
```

Guard and action IDs are emitted as class-level lambda fields. The generated lambdas are placeholders and are intended to be filled in or wired to real domain behavior.

The Project panel can export code in two styles:

- `Fluent chain` uses `FsmFactory.statesWithEvents().from(...).to(...).end()`.
- `Builder add calls` uses `ExTransitionTable.Builder().add(ExTransition(...))`.

## Future: Camunda BPMN Export

The editor model can be extended with a Camunda BPMN export target. The preferred path is to generate BPMN from the `.fsm.json` editor document, not to reverse-engineer already generated Java/Kotlin code.

A practical implementation should start as a generator-only feature:

- states become BPMN flow nodes or state marker tasks;
- event transitions become BPMN flows driven by message, signal, or user-triggered steps;
- guards become exclusive gateway conditions;
- actions become service tasks or worker/delegate calls;
- auto transitions become immediate internal flows;
- timeouts become timer events or timer-backed wait steps.

The hard parts are runtime semantics, not XML generation. The current FSM executes actions before state change, postActions after state change, supports chained auto transitions, sleeps before timeout transitions, and relies on ordered first-match transition selection. A Camunda export must model those details explicitly with gateways, service tasks, timers, and generated worker/delegate contracts.

Camunda 7 is the simpler first target for Java/Spring integration because service tasks can call Java delegates or Spring beans directly. Camunda 8 is a better modern orchestration target, but actions need job workers and more runtime infrastructure.
