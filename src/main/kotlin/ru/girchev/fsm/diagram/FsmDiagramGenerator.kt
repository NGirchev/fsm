package ru.girchev.fsm.diagram

import ru.girchev.fsm.impl.extended.ExTransitionTable

/**
 * Interface for FSM diagram generators
 */
interface FsmDiagramGenerator {
    /**
     * Generates diagram from transition table
     * @param transitionTable FSM transition table
     * @return string with diagram in PlantUML or Mermaid format
     */
    fun <STATE, EVENT> generate(transitionTable: ExTransitionTable<STATE, EVENT>): String
}