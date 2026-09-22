# FSM Visual Editor

Local visual finite state machine editor for building `.fsm.json` projects and generating Java/Kotlin factory code compatible with the Kotlin `fsm` library.

## Run

```bash
npm install
npm run dev
```

## Embedded example mode

The Spring Boot example reuses this application's graph editor at `/fsm-editor/`.
Its Gradle build runs the frontend build with `--mode example` and bundles the result as static
resources. That mode opens the application's `order` flow and uses its version API to create drafts,
save both runtime JSON and visual layout, and publish. Active/archived versions are read-only.
The standalone mode above continues to use local projects and Java/Kotlin generation.

See [the example instructions](../fsm-spring-boot-example/README.md#visual-order-editor) to run it.

## Browser regression tests

Requires Node 22+, Java 17+ and a running Docker daemon. From this directory:

```bash
npm ci
npx playwright install chromium
npm run test:e2e
```

The runner builds the example, starts a disposable PostgreSQL container and Spring application
under `/test`, and gives Vite a temporary projects directory. It never uses the development
database or `projects/`. Chromium runs headlessly with a fresh context per test. Servers,
container and temporary files are removed on completion or interruption. On failure, see
`playwright-report/`, `test-results/` (screenshots and traces) and `e2e-server.log`.
Pass Playwright options after `--`, for example `npm run test:e2e -- --grep 'delete draft'`.

| Scenarios | Tests |
| --- | --- |
| Project settings, validation, add/rename/describe/drag/delete states, keyboard deletion and cascading edges | `e2e/graph.spec.ts`, both modes |
| Connect handles, duplicate connections, edge selection/deletion, endpoints, event/auto trigger, guards/actions/post-actions, timeout units | `e2e/graph.spec.ts`, both modes |
| Event and behavior creation/rename/deletion, reference propagation, JSON import/export and invalid input | `e2e/graph.spec.ts`, both modes |
| Zoom, fit, minimap, interaction lock | `e2e/graph.spec.ts`, both modes |
| Create/save/discard/delete/cancel, reload/layout persistence, read-only versions, failure/retry paths | `e2e/order.spec.ts` |
| Publish validation, new order version and unchanged existing orders, protected active/archive versions | `e2e/order.spec.ts` |
| Autosave, browser fallback, project switch/delete, failed project requests, Java/Kotlin exports in both styles | `e2e/projects.spec.ts` |

Successful order operations use the real HTTP API and database. Failure scenarios deliberately
intercept only the request whose error handling is being tested. Unit tests remain `npm test`.

## GitHub Pages

The repository publishes this editor with the `GitHub Pages` workflow. The workflow builds the static Vite app from this directory and deploys `dist`.

Enable GitHub Pages in the repository settings with `GitHub Actions` as the source. The build sets `VITE_BASE_PATH` to `/<repository-name>/`, so project Pages URLs load bundled assets correctly.

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
