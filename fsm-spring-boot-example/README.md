# Spring Boot FSM Example

This is the repository's single executable Spring Boot example. It demonstrates an order FSM
loaded from versioned PostgreSQL `JSONB` configuration.

Application code and tests are Java. The example uses the Kotlin-based FSM core and starter as
libraries; Jackson's Kotlin module remains necessary to read their DTOs.

## Run

Create `.env` in the repository root using `.env.example` (merge the settings if `.env` already
exists). It sets `POSTGRES_PORT=55439` and `SERVER_PORT=18089` so the example can run alongside
other applications. Run the following from the repository root:

```bash
docker compose --env-file .env -f fsm-spring-boot-example/compose.yml up -d --wait
./gradlew :fsm-spring-boot-example:bootRun
```

The example database is available on `localhost:55439`, leaving the usual PostgreSQL port `5432`
available for other projects. Both Compose and the application use `POSTGRES_PORT` to override
this port; `DB_URL` can override the application's complete JDBC URL. With the defaults above,
`io.github.ngirchev:dotenv` loads `.env` from the working directory, then `../.env`, before Spring
starts. Existing JVM properties and environment variables take priority; the first loaded file
wins for duplicate keys. This supports `bootRun` and IDE launches from the repository root or
the example module. The local `.env` stays outside Git and the packaged JAR.

Flyway's single `V1` file creates the schema and two ready-to-use versions of the `order` flow:
`Fixed commission` (v1, 2% for every amount) and `Commission by amount` (v2, initially active).
The second version chooses 2% below `1000.00` or 1% from `1000.00` and shows the chosen branch
as a separate state. Both finish in `COMPLETED`.

`POST /api/orders` requires JSON with an explicit amount, for example `{"amount": 100.00}`, and always uses the active flow.
The request supplies only the amount. The Orders screen does not select a flow.

The guards and actions are Spring beans selected in the editor. Saving and publishing a draft changes
runtime behavior without creating Java beans in the browser. Older orders keep running on their pinned
flow version.

## Visual order editor

Open [http://localhost:18089/](http://localhost:18089/) after starting with
the supplied `.env.example` settings (without `SERVER_PORT`, Spring defaults to `8080`).
The example owns a web application with two tabs:

- **Orders** offers one **Run flow** button. It creates a real order against the active published
  version, sends `SUBMIT` and `FINISH`, and shows each confirmed transition, executed bean and changed
  value. Publish editor changes first. Synchronous automatic transitions are included. The order ID is
  remembered in the browser, so reloading restores its persisted history from the server. A failed
  request stops the run at the last confirmed state. Order responses include a request-local `trace`;
  `GET /api/orders/{id}/history` is the durable history.
- **Flow editor** embeds the starter's ready-made administration panel at `/fsm-admin/`.
  Switching tabs preserves the order and unsaved draft. The example registers `order` through
  `OrderAdminConfiguration`; the starter owns the version API and editor integration.

The second tab loads the real `order` flow from the version API. Use **Create draft**, edit the graph,
then **Save draft** and **Publish**. The version selector also opens existing drafts and archived
versions. Select an archived version and click **Make active** to switch between the prepared flows.
New orders then use that version; existing orders keep their pinned version. Published versions are
read-only; create a draft to change them. Save or discard local
changes before switching versions or publishing.
Use **Delete draft** to remove the selected draft (confirmation also discards its unsaved edits).
Active and archived versions cannot be deleted. Drafts are physically deleted. The next
number is the maximum remaining version plus one, so deleting the highest draft reuses its number.

### Complex order flows

Transition dropdowns list registered Spring `Guard<Order>`, `Action<Order>`, state-listener and
completion-listener beans by type. The example does not maintain a separate list of bean names.
Hover a bean to see its description from the bean definition. Selected beans can be replaced, removed,
or reordered. **Execution settings** provides separate dropdowns for state and completion listeners.
The playground pre-fills `amount` with `100.00` for a one-click demo, but sends it explicitly.
The API and stored order require an amount; the entity and database column have no default.
Guards select a 2% commission below `1000.00`
or a 1% commission from `1000.00`. Amounts must be non-negative with at most two decimal digits. Commission actions
set, rather than increment, the amount using decimal arithmetic and `HALF_UP` rounding.
The starter discovers the catalog directly from Spring beans; the application supplies no catalog DTOs.
`OrderFlowValidator` retains domain validation for publication and execution.
The catalog includes amount-threshold guards, 1% and 2% commission actions,
`recordOrderHistory`, and `logOrderNotification`. The latter only writes a demo message
to the application log; there is no notification client, queue, worker or delivery state.

The transition inspector shows branch priority and the execution order of guards, actions and
post-actions. **Up/Down** changes that order. The first matching branch wins; actions run before
the state changes, post-actions after. Timeout blocks the current call; it is not a durable timer.

Add `recordOrderHistory` under **State listeners** to persist every transition. Add
`logOrderNotification` as an action or post-action when the example should log an imitation
notification. Event requests still accept `{"event":"SUBMIT"}` and may add
`source` and `requestId`. `GET /api/orders/{id}/history` returns ordered, transactionally persisted
history. The response for an order includes amount and commission.

Execution bindings are stored under the optional `execution` property of the version:
`stateListeners` and `completionListeners`. Definitions without it run without listeners.
These prepared order bindings belong to the example; the starter preserves the configuration
and supports an event parser.

Run `./gradlew :fsm-spring-boot-example:test --tests '*OrderFlowJourneyIT'` for the ordered
HTTP journey on disposable PostgreSQL. The seed contains two ready versions; test scenarios
create and publish their versions using the API or UI.
See [the coverage matrix](../docs/tasks/order-editor-coverage.md).

The same JSON definition stores the executable table and optional `editor` metadata (node positions,
descriptions, IDs and editor settings). Saving and reopening a draft restores its layout, including
from another browser. Definitions without metadata receive an initial layout. The runtime table
remains authoritative when definitions are changed through the API. Guard/action IDs refer to
existing Spring beans; publication rejects unknown names.

Gradle builds the existing universal `fsm-visual-editor` and packages its static assets in the
starter JAR. Node.js/npm are required to build the starter from this checkout, but not for an
application consuming the published starter or for running the packaged example.
The Gradle editor tasks use Node/npm from `PATH`; on macOS they also check the standard Homebrew
locations (`/opt/homebrew/bin` and `/usr/local/bin`) for IDE launches without a shell environment.
For other installations, include the directory containing both Node and npm in the IDE's Gradle
process `PATH`. Node must be available to npm's build scripts as well.
The independent editor still supports local projects and Java/Kotlin export via `npm run dev`.
Opening `/fsm-admin/editor/index.html` directly opens the standalone editor; `/fsm-admin/` opens
the complete administration panel. All order-specific UI lives in `src/main/resources/static/example.js`.
The example no longer implements the iframe protocol or version controls itself. No extra editor
starter or server is required. The example's API is unauthenticated and intended for local demonstration.
For a secured application, follow the starter's [admin integration and security instructions](../fsm-spring-boot-starter/README.md#optional-administration-panel).

## Order execution

Each order records the flow version it was created with, so publishing a new version does not break in-progress orders.
When automatic execution is enabled, creating an order runs automatic transitions from its initial
state and saves their result in the creation transaction. Exceeding the runtime limit rolls back
the order creation.

```bash
curl -X POST http://localhost:18089/api/orders

curl -X POST http://localhost:18089/api/orders/1/events \
  -H 'Content-Type: application/json' \
  -d '{"event":"SUBMIT"}'

curl -X POST http://localhost:18089/api/orders/1/events \
  -H 'Content-Type: application/json' \
  -d '{"event":"FINISH"}'
```

Flow management endpoints are under `/fsm-admin/api/flows/{flowKey}/versions`. Only registered keys
are exposed. A draft is editable until
`POST /fsm-admin/api/flows/{flowKey}/versions/{version}/publish` validates it and atomically makes it active.
`DELETE /fsm-admin/api/flows/{flowKey}/versions/{version}` removes a draft with `204`; missing versions return
`404`, and active/archived versions return `409`.

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
          "state": "IN_PROGRESS",
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
The starter's `FlowLoader` delegates restoration to `SpringFsmJsonSerializer`, which uses the core converter
and resolves ordinary `Action` and `Guard` Spring beans by name. Use this serializer to export
tables containing ordinary Spring beans; the core serializer handles identifiable handlers only.
Unknown beans reject publication rather than silently removing behavior.

`table.maxImmediateAutoTransitions` defaults to `0`, which leaves automatic transitions unlimited.
A positive value caps consecutive automatic transitions at runtime.
