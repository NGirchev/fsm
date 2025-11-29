package ru.girchev.fsm.diagram

import ru.girchev.fsm.impl.extended.ExTransitionTable
import java.nio.file.Files
import java.nio.file.Path

/**
 * Generates PlantUML diagram for the given transition table
 * @return string with PlantUML diagram
 */
fun <STATE, EVENT> ExTransitionTable<STATE, EVENT>.toPlantUml(): String {
    return PlantUmlFsmGenerator().generate(this)
}

/**
 * Generates Mermaid diagram for the given transition table
 * @return string with Mermaid diagram
 */
fun <STATE, EVENT> ExTransitionTable<STATE, EVENT>.toMermaid(): String {
    return MermaidFsmGenerator().generate(this)
}

fun <STATE, EVENT> ExTransitionTable<STATE, EVENT>.toPlantUml(path: Path) {
    Files.write(path, toPlantUml().toByteArray())
}

fun <STATE, EVENT> ExTransitionTable<STATE, EVENT>.toMermaid(path: Path) {
    Files.write(path, toMermaid().toByteArray())
}

/**
 * Prints PlantUML diagram to console
 */
fun <STATE, EVENT> ExTransitionTable<STATE, EVENT>.printPlantUml() {
    println(toPlantUml())
}

/**
 * Prints Mermaid diagram to console
 */
fun <STATE, EVENT> ExTransitionTable<STATE, EVENT>.printMermaid() {
    println(toMermaid())
}