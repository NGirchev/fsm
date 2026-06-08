# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
