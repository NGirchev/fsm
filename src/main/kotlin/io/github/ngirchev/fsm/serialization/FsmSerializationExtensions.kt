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
 * Deserializes JSON string to ExTransitionTable
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
 * Deserializes JSON file to ExTransitionTable
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
 * Deserializes JSON from InputStream to ExTransitionTable
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
 * Prints JSON representation to console
 */
fun <STATE, EVENT> ExTransitionTable<STATE, EVENT>.printJson() {
    println(toJson())
}
