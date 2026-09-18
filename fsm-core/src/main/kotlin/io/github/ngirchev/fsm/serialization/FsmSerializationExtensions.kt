package io.github.ngirchev.fsm.serialization

import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Serializes ExTransitionTable to JSON string
 */
fun <STATE, EVENT> ExTransitionTable<STATE, EVENT>.toJson(): String {
    return FsmJsonSerializer().serialize(this)
}

/**
 * Serializes ExTransitionTable to JSON and writes to file
 */
fun <STATE, EVENT> ExTransitionTable<STATE, EVENT>.toJson(path: Path) {
    Files.write(path, toJson().toByteArray())
}

/**
 * Serializes ExTransitionTable to JSON and writes to OutputStream
 */
fun <STATE, EVENT> ExTransitionTable<STATE, EVENT>.toJson(output: OutputStream) {
    FsmJsonSerializer().serialize(this, output)
}

/**
 * Deserializes JSON string to ExTransitionTable.
 * Action and guard factories are optional best-effort helpers: missing factories or unknown IDs
 * drop only the affected actions/guards.
 */
fun <STATE, EVENT> String.fromJson(
    stateParser: (String) -> STATE,
    eventParser: (String) -> EVENT,
    actionFactory: ActionFactory<STATE>? = null,
    guardFactory: GuardFactory<STATE>? = null
): ExTransitionTable<STATE, EVENT> {
    return FsmJsonSerializer().deserialize(this, stateParser, eventParser, actionFactory, guardFactory)
}

/**
 * Deserializes JSON string to ExTransitionTable.
 * Action and guard factories are optional best-effort helpers. Scheduler restoration is strict:
 * if JSON contains a scheduler ID, [autoTransitionSchedulerFactory] must resolve it.
 */
fun <STATE, EVENT> String.fromJson(
    stateParser: (String) -> STATE,
    eventParser: (String) -> EVENT,
    actionFactory: ActionFactory<STATE>?,
    guardFactory: GuardFactory<STATE>?,
    autoTransitionSchedulerFactory: AutoTransitionSchedulerFactory<STATE>?,
): ExTransitionTable<STATE, EVENT> {
    return FsmJsonSerializer().deserialize(
        this,
        stateParser,
        eventParser,
        actionFactory,
        guardFactory,
        autoTransitionSchedulerFactory,
    )
}

/**
 * Deserializes JSON file to ExTransitionTable with the same best-effort action/guard restore as
 * [String.fromJson].
 */
fun <STATE, EVENT> Path.fromJson(
    stateParser: (String) -> STATE,
    eventParser: (String) -> EVENT,
    actionFactory: ActionFactory<STATE>? = null,
    guardFactory: GuardFactory<STATE>? = null
): ExTransitionTable<STATE, EVENT> {
    val json = Files.readString(this)
    return json.fromJson(stateParser, eventParser, actionFactory, guardFactory)
}

/**
 * Deserializes JSON file to ExTransitionTable with strict scheduler restore when scheduler IDs
 * are present.
 */
fun <STATE, EVENT> Path.fromJson(
    stateParser: (String) -> STATE,
    eventParser: (String) -> EVENT,
    actionFactory: ActionFactory<STATE>?,
    guardFactory: GuardFactory<STATE>?,
    autoTransitionSchedulerFactory: AutoTransitionSchedulerFactory<STATE>?,
): ExTransitionTable<STATE, EVENT> {
    val json = Files.readString(this)
    return json.fromJson(
        stateParser,
        eventParser,
        actionFactory,
        guardFactory,
        autoTransitionSchedulerFactory,
    )
}

/**
 * Deserializes JSON from InputStream to ExTransitionTable with the same best-effort
 * action/guard restore as [String.fromJson].
 */
fun <STATE, EVENT> InputStream.fromJson(
    stateParser: (String) -> STATE,
    eventParser: (String) -> EVENT,
    actionFactory: ActionFactory<STATE>? = null,
    guardFactory: GuardFactory<STATE>? = null
): ExTransitionTable<STATE, EVENT> {
    return FsmJsonSerializer().deserialize(this, stateParser, eventParser, actionFactory, guardFactory)
}

/**
 * Deserializes JSON from InputStream to ExTransitionTable with strict scheduler restore when
 * scheduler IDs are present.
 */
fun <STATE, EVENT> InputStream.fromJson(
    stateParser: (String) -> STATE,
    eventParser: (String) -> EVENT,
    actionFactory: ActionFactory<STATE>?,
    guardFactory: GuardFactory<STATE>?,
    autoTransitionSchedulerFactory: AutoTransitionSchedulerFactory<STATE>?,
): ExTransitionTable<STATE, EVENT> {
    return FsmJsonSerializer().deserialize(
        this,
        stateParser,
        eventParser,
        actionFactory,
        guardFactory,
        autoTransitionSchedulerFactory,
    )
}

/**
 * Prints JSON representation to console
 */
fun <STATE, EVENT> ExTransitionTable<STATE, EVENT>.printJson() {
    println(toJson())
}
