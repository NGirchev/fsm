# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[Unreleased]: https://github.com/NGirchev/fsm/compare/v1.0.2...HEAD
[1.0.2]: https://github.com/NGirchev/fsm/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/NGirchev/fsm/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/NGirchev/fsm/releases/tag/v1.0.0

