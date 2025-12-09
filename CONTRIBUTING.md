# Contributing to FSM

Thank you for your interest in contributing to FSM! This document provides guidelines and instructions for contributing to the project.

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment for all contributors.

## Getting Started

### Prerequisites

* **Java 8 or higher** (OpenJDK recommended)
* **Gradle 7.0+**
* **Git**

### Setting Up the Development Environment

1. Fork the repository on GitHub
2. Clone your fork locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/fsm.git
   cd fsm
   ```
3. Add the upstream repository:
   ```bash
   git remote add upstream https://github.com/NGirchev/fsm.git
   ```
4. Build the project:
   ```bash
   ./gradlew build
   ```

## Development Workflow

### Creating a Branch

Create a feature branch from `master`:

```bash
git checkout -b feature/your-feature-name
```

Use descriptive branch names:
- `feature/add-new-fsm-type` - for new features
- `fix/correct-state-transition` - for bug fixes
- `docs/update-readme` - for documentation changes

### Making Changes

1. Make your changes to the codebase
2. Write or update tests for your changes
3. Ensure all tests pass:
   ```bash
   ./gradlew test
   ```
4. Run code quality checks:
   ```bash
   ./gradlew detekt
   ./gradlew lintKotlinMain lintKotlinTest
   ```
5. Verify code coverage:
   ```bash
   ./gradlew jacocoTestReport jacocoTestCoverageVerification
   ```

### Coding Standards

#### Kotlin Style

* Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
* Use the official Kotlin code style (configured in `gradle.properties`)
* The project uses `kotlinter` for automatic formatting - run `./gradlew formatKotlinMain formatKotlinTest` before committing

#### Code Quality

* **Detekt**: Static code analysis for Kotlin
  - Run: `./gradlew detekt`
  - Configuration: `detekt.yml`
  - Fix all issues before submitting

* **Kotlinter**: Kotlin linter and formatter
  - Run: `./gradlew lintKotlinMain lintKotlinTest`
  - Auto-format: `./gradlew formatKotlinMain formatKotlinTest`

#### Documentation

* Add KDoc comments for all public APIs
* Update README.md if you add new features or change existing behavior
* Update CHANGELOG.md with your changes (see below)

#### Testing

* Write tests for all new features
* Write tests for bug fixes
* Maintain or improve code coverage (minimum 80% line coverage, 70% branch coverage)
* Use descriptive test names that explain what is being tested

Example:
```kotlin
@Test
fun `should transition to next state when valid event is triggered`() {
    // Given
    val fsm = createFsm("INITIAL")
    
    // When
    fsm.onEvent("VALID_EVENT")
    
    // Then
    assertEquals("NEXT_STATE", fsm.getState())
}
```

### Updating CHANGELOG.md

When making changes, update `CHANGELOG.md`:

1. Add your changes under the `[Unreleased]` section
2. Use the following categories:
   - `Added` - for new features
   - `Changed` - for changes in existing functionality
   - `Deprecated` - for soon-to-be removed features
   - `Removed` - for now removed features
   - `Fixed` - for any bug fixes
   - `Security` - for vulnerability fixes

Example:
```markdown
## [Unreleased]

### Added
- New FSM type for event-driven state machines

### Fixed
- Corrected state transition validation logic
```

### Committing Changes

Write clear, descriptive commit messages:

```
Short summary (50 chars or less)

More detailed explanation if needed. Wrap it to about 72
characters or so. In some contexts, the first line is treated as the
subject of the commit and the rest of the text as the body.

- Bullet points are okay, too
- Use imperative mood: "Add feature" not "Added feature"
```

### Submitting a Pull Request

1. Push your branch to your fork:
   ```bash
   git push origin feature/your-feature-name
   ```

2. Create a Pull Request on GitHub:
   - Go to the original repository
   - Click "New Pull Request"
   - Select your branch
   - Fill out the PR template (if available)
   - Describe your changes clearly

3. Ensure your PR:
   - Has a clear title and description
   - References any related issues
   - Includes tests for new features
   - Passes all CI checks
   - Updates documentation if needed
   - Updates CHANGELOG.md

4. Respond to code review feedback:
   - Address all comments
   - Make requested changes
   - Push updates to your branch (the PR will update automatically)

## Review Process

* All PRs require review before merging
* Maintainers will review your code for:
  - Correctness
  - Code quality and style
  - Test coverage
  - Documentation
* Be patient and responsive to feedback

## Release Process

Releases are managed by project maintainers. If you want to suggest a release:

1. Ensure all changes are merged to `master`
2. Update version in `gradle.properties`
3. Update CHANGELOG.md with the release version
4. Create a GitHub issue requesting a release

### Creating a GitHub Release

For maintainers creating a release:

1. Build the project and generate artifacts:
   ```bash
   ./gradlew build publishToMavenLocal
   ```

2. Create GitHub release with changelog:
   ```bash
   gh release create v1.0.0 -F CHANGELOG.md
   ```

3. Attach artifacts and their signatures (for OpenSSF Security Score):
   ```bash
   gh release upload v1.0.0 target/*.jar target/*.jar.asc --clobber
   ```

## Questions?

If you have questions about contributing:

* Open an issue on GitHub
* Check existing issues and discussions
* Review the README.md for project information

Thank you for contributing to FSM! 🎉

