# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Changes since the published 1.2.0 release. This section describes the unreleased development version.

### Added
- Per-transition `.auto()` opt-in for synchronous eventless transitions and an optional
  `maxImmediateAutoTransitions` runtime limit. The default limit of `0` leaves chains unlimited.
- A Spring Boot starter with automatic bean registration and JSON serialization that restores
  guards, actions and post-actions by Spring bean name, including their proxies and dependencies.
- Database-independent versioned flow management: draft creation, editing and deletion, validation,
  publication and activation of archived versions through an application-provided `FlowStore`.
- An optional Spring administration API for flow versions with typed Spring bean catalogs,
  and a configurable URL prefix. The starter serves no UI. Authentication, authorization, CSRF and
  CORS remain the host application's responsibility.
- Backend connection in the visual editor: `?backend=` or `config.js` points a separately deployed
  editor at an application's administration API for draft, publish and activation workflows. Requests
  send cookies by default; `config.js` can replace the request function to add credentials.
  A Dockerfile packages the static editor with nginx.
- Automatic background processing for declared `FsmTaskProcessor` beans, with a separate transaction
  per task, configurable polling, bounded queue draining and an opt-out for external job runners.
  Applications supply task handlers and storage through `FsmTaskHandler` and `FsmTaskStore`.
- A Java Spring Boot example using JPA, PostgreSQL and versioned JSONB flow definitions. Orders retain
  their flow version, persist transition history and demonstrate guarded commission branches.
- A reusable iframe protocol for embedding the visual editor, with origin/session checks,
  read-only mode and typed behavior catalogs. Saved flow definitions retain layout and state colors.

### Changed
- Split the build into the reusable `fsm` core, `fsm-spring-boot-starter` and executable
  `fsm-spring-boot-example`. Core Maven coordinates remain `io.github.ngirchev:fsm` and its JVM target
  remains Java 11; Spring modules require Java 17+ and target Spring Boot 3.5.
- Preserved global `autoTransitionEnabled(true)` behavior for all eventless transitions while
  allowing individual `.auto()` transitions when global automatic execution is disabled.
- Updated the visual editor and Java/Kotlin generators for local automatic transitions and chain
  limits. The editor also supports explicit branch/handler ordering, execution listeners and
  persisted editor metadata.

### Fixed
- Spring task processing now rolls back checked handler exceptions such as `IOException` as well
  as runtime exceptions. A failed processor no longer prevents other processors from running;
  it is retried on the next poll.
- Generated factories preserve transition priority while placing transitions reachable from the
  configured initial state first.

### Removed / migration from 1.2.0
- **Breaking change:** removed `AutoTransitionScheduler`, builder `autoTransitionScheduler`
  configuration, scheduler-bearing constructors, deferred auto-transition DSL methods and scheduler
  serialization. Code using these APIs must be migrated before upgrading.
- Keep `.auto()` for synchronous progression. For durable asynchronous work, persist a task together
  with domain state and resume the FSM with an ordinary event from a task handler. The starter
  supplies the processing lifecycle; the application owns task persistence and locking.

See the [starter integration guide](fsm-spring-boot-starter/README.md) and
[runnable example](fsm-spring-boot-example/README.md) for the storage and transaction contracts.

## [1.2.0] - 2026-06-08

### Added
- Added `AutoTransitionScheduler` so auto transitions can be scheduled after an external commit boundary while preserving synchronous auto transitions by default.
- Added builder-level `autoTransitionScheduler` configuration for deferred auto transitions.
- Added documentation and an integration test for deferring a domain auto transition until after an intermediate state is persisted, including a Spring `afterCommit` example.
- Added `fsm-visual-editor`, a local visual FSM editor for designing finite state machine flows.
- Added `.fsm.json` editor project import/export, autosave, recent-project loading, and local project storage.
- Added Java and Kotlin factory code generation from visual FSM diagrams.
- Added generated state/event enums, `StateContext` domain DTOs, guard/action placeholders, auto transitions, timeouts, and post-action support.
- Added validation and unit coverage for editor IDs, document import normalization, storage, project API calls, layout, and Java/Kotlin generators.
- Added README documentation and visual editor GIF for the new editor workflow.

### Changed
- Renamed the frontend tool to `fsm-visual-editor` and aligned package names, local storage keys, documentation, and UI labels.
- Updated README discovery text for Kotlin finite state machines, visual FSM editing, and Java/Kotlin code generation.
- Replaced hardcoded installation versions with a `VERSION` placeholder that points users to the latest Maven Central release.
- Removed maintainer-only Maven Central publishing instructions from the public README.

### Fixed
- Preserved runtime `TypedEvent` payloads in `currentTransition` for event actions and post-actions.
- Preserved transition insertion order in built transition tables while isolating them from later builder mutations.
- Clarified the Spring `afterCommit` scheduler example to use `PROPAGATION_REQUIRES_NEW` for persisted auto-transition results.
- Prevented duplicate visual-editor transitions when the same states are connected twice, and surfaced matching imported duplicates in validation.
- Shortened generated PlantUML and Mermaid labels for unnamed guard/action lambdas while preserving explicit named behavior labels.
- Fixed generated duplicate event/guard/action IDs so UI-created refs remain Java/Kotlin identifier-safe.
- Rejected qualified names for generated nested domain and state types.
- Hardened `.fsm.json` import and saved-document loading to reject malformed array elements before normalization.

### Security
- Upgraded Jackson dependencies to 2.21.1 to resolve the `jackson-core` async parser DoS advisory (`GHSA-72hv-8253-57qq`).

## [1.1.0] - 2026-04-01

### Changed
- Renamed `condition()` to `onCondition()` in transition builder (breaking change)
- Upgraded Kotlin to 2.2.0
- Upgraded Jackson to 2.19.0
- Upgraded SLF4J to 2.0.17, logback-classic to 1.5.32
- Upgraded mockito-kotlin to 5.4.0, mockk to 1.14.3, JUnit Jupiter Params to 5.12.2
- Upgraded Detekt to 1.23.8, Vanniktech Maven Publish to 0.34.0

### Added
- FSM serialization/deserialization support via Jackson
- Listener support for state transitions

### Fixed
- Removed unused kotlinter CI step (plugin was not configured)

## [1.0.2] - 2025-12-11

### Security
- Fixed CVE-2024-12798 (JaninoEventEvaluator vulnerability in logback-classic)
- Changed logging dependencies from `implementation` to `compileOnly` to avoid transitive vulnerabilities
- Updated logback-classic to 1.5.20 (vulnerability fixed in 1.5.13+)

### Changed
- Logging dependencies (kotlin-logging-jvm and logback-classic) are now provided as `compileOnly` dependencies
- Logging dependencies available at runtime for local testing via `testImplementation`

### Improved
- Improved artifact signing process to skip GPG signing for local Maven repository publishing (`publishToMavenLocal`)
- Enhanced build configuration comments with security information

## [1.0.1] - 2025-12-11

### Changed
- Updated Kotlin from 1.6.21 to 1.9.25 for better compatibility with Gradle 8.10 and Maven Publish plugin
- Updated Detekt from 1.21.0-RC2 to 1.23.7
- Updated kotlin-logging-jvm from 2.0.11 to 3.0.5
- Updated logback-classic from 1.5.19 to 1.5.20
- Updated mockito-kotlin from 3.2.0 to 5.3.1
- Updated mockk from 1.9.3 to 1.13.11
- Updated JUnit Jupiter Params from 5.8.1 to 5.11.0

### Improved
- Migrated to `com.vanniktech.maven.publish` plugin for simplified Maven Central publishing
- Improved build configuration with explicit Java 11 compatibility settings
- Enhanced documentation with better installation instructions placement
- Added support for alternative Maven Central publishing configuration format

## [1.0.0] - Initial Release

### Added
- Basic FSM implementation
- Extended FSM implementation with domain support
- State transition table builder
- Event handling
- Guard conditions
- Actions and post-actions
- Exception handling for invalid transitions

[Unreleased]: https://github.com/NGirchev/fsm/compare/v1.2.0...HEAD
[1.2.0]: https://github.com/NGirchev/fsm/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/NGirchev/fsm/compare/v1.0.2...v1.1.0
[1.0.2]: https://github.com/NGirchev/fsm/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/NGirchev/fsm/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/NGirchev/fsm/releases/tag/v1.0.0
