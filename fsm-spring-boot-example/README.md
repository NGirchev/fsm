# Spring Boot FSM Example

This is the repository's single executable Spring Boot example. It demonstrates both a runtime FSM
loaded from versioned PostgreSQL `JSONB` configuration and a small transactional JPA workflow
continued by the starter's background worker.

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

Flyway creates one initial active `order` version. It does not add flow versions for the demo.

`POST /api/orders` accepts optional JSON `{"approved": false}` (default: `true`). After
`SUBMIT`, the same `FINISH` event selects one of two branches from `IN_PROGRESS`:

- `orderApproved` checks `approved=true` and selects `SENT`.
- `orderNotApproved` checks `approved=false` and selects `FAILED`.

Both conditions are Spring `Guard<Order>` beans. Configure the branches in a draft to use them.
The `SENT` branch uses the
`notifyOrderCompleted` post-action, which logs a **demo** notification and sets
`notificationSent=true`, visible in `GET /api/orders/{id}`. It does not send an email or message.
The handler names appear in the editor's Behavior panel. To build the branches manually,
create a draft, add `SENT` and `FAILED` states and two `FINISH` transitions, then add both
guard names using **Add guard** and select the appropriate checkbox on each transition.
Attach `notifyOrderCompleted` under **Post actions** only on the `SENT` branch, save and publish.
This changes the flow at runtime using existing registered beans; it does not create Java
beans from the browser. Older pinned versions keep their previous behavior.

## Visual order editor

Open [http://localhost:18089/fsm-editor/](http://localhost:18089/fsm-editor/) after starting with
the supplied `.env.example` settings (without `SERVER_PORT`, Spring defaults to `8080`).
The page loads the real `order` flow from the version API. Use **Create draft**, edit the graph,
then **Save draft** and **Publish**. The version selector also opens existing drafts and archived
versions. Published versions are read-only; create a draft to change them. Save or discard local
changes before switching versions or publishing.
Use **Delete draft** to remove the selected draft (confirmation also discards its unsaved edits).
Active and archived versions cannot be deleted. Allocated version numbers are never reused.

The same JSON definition stores the executable table and optional `editor` metadata (node positions,
descriptions, IDs and editor settings). Saving and reopening a draft restores its layout, including
from another browser. Definitions without metadata receive an initial layout. The runtime table
remains authoritative when definitions are changed through the API. Guard/action IDs refer to
existing Spring beans; publication rejects unknown names. There are no behavior beans in the seeded
order flow.

Gradle builds the existing `fsm-visual-editor` in `example` mode and packages its static assets with
the application. Node.js/npm are required to build the example, but not to run the packaged JAR.
The independent editor still supports local projects and Java/Kotlin export via `npm run dev`.
No extra editor starter or server is required. The example's existing API is unauthenticated and
intended for local demonstration.

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

Flow management endpoints are under `/api/flows/{flowKey}/versions`. A draft is editable until
`POST /api/flows/{flowKey}/versions/{version}/publish` validates it and atomically makes it active.
`DELETE /api/flows/{flowKey}/versions/{version}` removes a draft with `204`; missing versions return
`404`, and active/archived versions return `409`.

## Transactional workflow

For a small starter example, begin with the six files in
[src/main/java/io/github/ngirchev/fsm/example/workflow](src/main/java/io/github/ngirchev/fsm/example/workflow).
The separate `definition` and `order` packages demonstrate the more advanced JSON configuration API.

This scenario has one entity and two transitions:

```text
NEW -- START (business service) --> NOTIFY -- ADVANCE (background notification) --> END
```

Read these three files first:

1. `ExternalWorkflowConfiguration` defines the two FSM transitions, the notification client and
   the starter's `FsmTaskProcessor` bean.
2. `ExternalWorkflowService` creates a workflow and sends `START` in a transaction.
3. `ExternalWorkflowRepository` implements `FsmTaskStore<ExternalWorkflow>`: it selects a workflow
   in `NOTIFY` with `FOR UPDATE SKIP LOCKED`. The workflow itself is the pending work; there is no
   separate task entity or task status.

The starter worker calls `processNext()` in the background. It claims one workflow, sends
`ADVANCE`, saves the result and commits. A successful notification moves the workflow to `END`,
so it is no longer selected. If the notification throws a runtime exception, the transaction rolls
back and the workflow remains in `NOTIFY` for retry. Rolling back `start()` leaves the workflow
in `NEW`, with nothing for the worker to process.

JPA saves state changes in the enclosing transaction; no persistence actions are needed in the
transition table. Notifications receive a stable key,
`external-workflow:<id>:notification`, so a real client can deduplicate retries after an external
success followed by a database failure.

This deliberately small design assumes one pending step per workflow. A separate task table is
useful when a process needs multiple queued commands, scheduled execution or task history.

Run it with:

```bash
FSM_DEMO_RUNNER_ENABLED=true ./gradlew :fsm-spring-boot-example:bootRun
```

The default notification client logs a message. The worker polls with a five-second delay by
default; `FSM_TASKS_ENABLED=false` disables it.

Flyway initializes the example with two files: `V1` creates the current schema,
and `V2` inserts the order flow in the core JSON format.

Run its PostgreSQL integration tests with:

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

Publishing a definition with automatic execution enabled requires a positive
`table.maxImmediateAutoTransitions`. The core runtime enforces that limit while running transitions.
