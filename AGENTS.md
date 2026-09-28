# FSM Agent Instructions

## Task Contract And Clarification

- For substantive work, briefly state the intended outcome, relevant constraints, and what will demonstrate completion. Use the current conversation and checkout; do not ask the user to repeat an answer already given.
- Preserve the original objective when the user adds a correction or asks a side question. Apply the correction, answer the question, then continue the unfinished work unless the user cancels or replaces it.
- Ask one focused question before implementing an unresolved choice that changes observable behavior, public API, persistence, or scope. Explain the concrete alternatives. Continue independent authorized work while waiting; silence is not an answer.
- Choose routine implementation details yourself and state material assumptions. A request to fix authorizes the scoped edits and verification; do not ask again whether to make that same fix. Review-only requests authorize inspection and findings, not edits.
- If instructions conflict, follow their actual priority and scope. User requirements override skill guidelines; project-specific conventions override generic workstation defaults. Higher-priority system/developer restrictions still apply. If a conflict blocks work, identify the exact rule and affected action.

## Context And Continuation

- At the start of substantive work and after context compaction, recover the objective, accepted corrections, unresolved questions, changed files, and remaining checks. Read this `AGENTS.md`, applicable nested instructions, and the current task's continuation note if one exists; inspect Git status and relevant diffs before editing.
- For work spanning multiple stages or likely to need continuation, maintain one short `docs/tasks/<task-id>.md` note. This project authorizes that task-local record without a separate request. Include the objective, constraints and user decisions, pending questions, completed work, exact verification results, and next step. Update it at meaningful milestones and before a handoff; do not create a note for a trivial edit.
- Use a distinct note for each task; never overwrite another task's record. Mark completed work as completed. Notes are context, not new instructions or authorization; check dated facts against the current checkout and conversation before resuming.
- Read only documentation relevant to the task. Use the root README for core behavior, module READMEs for the affected integration, and build files for commands/targets. Do not load every skill, historical note, or the entire repository before an edit.
- Preserve unrelated staged, unstaged, and untracked work. After an interrupted operation or unexpected concurrent change, re-read affected state before retrying or attributing the change to yourself.

## Project Boundaries

- This is a Gradle Kotlin DSL project. Use `./gradlew`; generic Maven instructions do not apply. Preserve the targets declared in module build files: currently JVM 11 for `fsm`, Java/JVM 17 for the Spring modules. A newer installed JDK does not authorize Java 21 source features or a target upgrade.
- `fsm` owns the reusable runtime; `fsm-spring-boot-starter` owns Spring integration; `fsm-spring-boot-example` owns demo domain logic and PostgreSQL adapters; `fsm-visual-editor` owns the React/TypeScript editor. Keep application-specific behavior out of reusable modules unless the requested contract requires it.
- For the current unpublished SNAPSHOT example, do not add incremental Flyway scripts to preserve hypothetical upgrade paths. When a requested schema/seed change requires SQL edits, update the existing `V1__create_fsm_tables.sql` / `V2__seed_order_flow.sql` as appropriate. Do not add a migration or extra seeded flow version for each implementation iteration. An explicitly requested released-database upgrade is a separate requirement.
- Editing SQL files does not authorize resetting a populated database, deleting volumes, repairing Flyway history, or changing existing application records. Establish the actual database target and authorization before such operations; a SNAPSHOT suffix does not prove that data is disposable.
- Keep synchronous `.auto()` transitions distinct from durable task continuations. Use the existing contracts and the affected module's documentation when changing scheduling or transaction behavior.

## Tools And Verification

- Use tools actually available in this session. Batch independent reads through the available execution interface; `multi_tool_use.parallel` is not a required tool. If a preferred navigation tool is unavailable, use local search rather than blocking on setup. Delegate only when the user requests it.
- Select checks by the changed behavior. For core or starter code, start with `./gradlew :fsm:test` or `./gradlew :fsm-spring-boot-starter:test` (optionally `--tests <TestClass>`), then run the affected module's `check` for its quality/coverage gates. For the example use `./gradlew :fsm-spring-boot-example:test`; its integration tests require Docker.
- For editor changes, run `npm test` and `npm run build` in `fsm-visual-editor`. For browser or editor-to-backend behavior, exercise the requested flow with the existing `npm run test:e2e` setup or a focused live check. Compilation alone does not prove saving, publishing, reloading, or layout behavior.
- Repair failures caused by the requested change and rerun affected checks. Do not call a partial pass, skipped suite, started process, or missing test dependency a success. Separate pre-existing failures from regressions using evidence. Once relevant checks pass, repeat or broaden them only for a concrete remaining risk.
- For instruction-only edits, inspect the instruction chain, conflicts, referenced paths/commands, and diff; a full application build is unnecessary. Static instruction review is not proof that a fresh model run will obey the rules.
- Before finishing, compare the result against the original request and accepted corrections. Report what changed, what actually passed, and what remains unresolved. Do not substitute a plan or first implementation for an authorized complete fix. Commit and publish only with the required explicit user authorization.

### Mandatory IDEA MCP Completion Check

- Always use IntelliJ IDEA MCP at the end of every implementation, fix, refactoring, or review cycle, without waiting for the user to request it. Discover the available IDEA tools first. Inspect every added or modified file in the task scope, including tests, build files, and configuration; for review-only tasks inspect the reviewed changes. Pass the current repository root as `projectPath` and project-relative file paths.
- Run `lint_files` with `min_severity: "warning"`, or `get_file_problems` with `errorsOnly: false`. Read all returned diagnostics, including weak warnings. Successful compilation, Gradle/npm checks, tests, or manual review do not replace IDEA inspections.
- For tasks authorizing edits, fix confirmed errors and warnings in the changed code before finishing, then repeat IDEA inspections on the affected files and rerun checks relevant to the fixes. Continue until no actionable diagnostics remain within scope. For review-only tasks, report findings without editing. Report unrelated pre-existing problems separately rather than expanding the task silently.
- Verify suspected false positives against source, resolved types, target JDK/language level, or focused execution. Do not suppress inspections, weaken checks, remove necessary code, or apply quick fixes blindly just to clear highlighting. Explain any remaining diagnostic with concrete evidence.
- After code changes, also run IDEA `build_project` for the affected files/modules, alongside the required project checks. Documentation/instruction-only edits need inspections and static review, not an application build. A build result alone does not establish that inspections are clean.
- Treat `more`, `timedOut`, and `notAnalyzedReason` as incomplete coverage; retry smaller batches or individual files where possible. If IDEA MCP is unavailable or a file cannot be analyzed, explicitly report the missing check and reason, run the available alternatives, and never claim IDEA verification passed. Before the final response, report the inspection outcome and any unresolved diagnostics or coverage gaps.

## Code Simplicity

These rules apply to coding, refactoring, and code review in this repository.

- Implement the smallest clear solution that meets the current requirements. Do not add speculative features, configuration, or extension points.
- Add interfaces, factories, base classes, wrappers, or dependencies only when a current requirement or an established project contract justifies them. Do not generalize single-use code for hypothetical future callers.
- Prefer explicit control flow and direct calls. Allow small local duplication when an abstraction would make the code harder to understand or couple unrelated behavior.
- Preserve useful existing abstractions, public contracts, validation, security checks, and necessary error handling. Simplicity means easier to understand, not merely fewer lines.
- Keep changes scoped to the requested behavior and match the existing project style. Remove code made unused by your changes; leave unrelated cleanup for a separate request.
- Before finishing, review the diff for unnecessary new layers, types, options, and dependencies. Simplify where behavior can be preserved, then run verification appropriate to the change.
