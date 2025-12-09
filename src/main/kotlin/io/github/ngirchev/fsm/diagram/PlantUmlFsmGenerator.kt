package io.github.ngirchev.fsm.diagram

import io.github.ngirchev.fsm.impl.extended.ExTransition
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable

/**
 * PlantUML diagram generator for FSM
 */
class PlantUmlFsmGenerator : FsmDiagramGenerator {

    override fun <STATE, EVENT> generate(transitionTable: ExTransitionTable<STATE, EVENT>): String {
        val sb = StringBuilder()
        sb.appendLine("@startuml")
        sb.appendLine()

        // Collect all states and their actions/postActions
        val stateActions = mutableMapOf<STATE, MutableList<String>>()
        val statePostActions = mutableMapOf<STATE, MutableList<String>>()

        transitionTable.transitions.forEach { (_, transitions) ->
            transitions.forEach { transition ->
                val targetState = transition.to.state

                // Collect actions for target state
                if (transition.to.actions.isNotEmpty()) {
                    stateActions.getOrPut(targetState) { mutableListOf() }.addAll(
                        transition.to.actions.map { it.toString() }
                    )
                }

                // Collect postActions for target state
                if (transition.to.postActions.isNotEmpty()) {
                    statePostActions.getOrPut(targetState) { mutableListOf() }.addAll(
                        transition.to.postActions.map { it.toString() }
                    )
                }
            }
        }

        // Collect all states
        val allStates = mutableSetOf<STATE>()
        transitionTable.transitions.forEach { (from, transitions) ->
            allStates.add(from)
            transitions.forEach { transition ->
                allStates.add(transition.to.state)
            }
        }

        // Define states with their actions/postActions
        allStates.forEach { state ->
            val stateIdValue = stateId(state)
            val actions = stateActions[state] ?: emptyList()
            val postActions = statePostActions[state] ?: emptyList()

            if (actions.isEmpty() && postActions.isEmpty()) {
                sb.appendLine("state \"$state\" as $stateIdValue")
            } else {
                sb.appendLine("state \"$state\" as $stateIdValue {")

                // Output actions
                actions.forEach { action ->
                    sb.appendLine("  $stateIdValue : ▶ $action")
                }

                // Output postActions
                postActions.forEach { postAction ->
                    sb.appendLine("  $stateIdValue : ◀ $postAction")
                }

                sb.appendLine("}")
            }
        }
        sb.appendLine()

        // Add transitions
        transitionTable.transitions.forEach { (from, transitions) ->
            transitions.forEach { transition ->
                sb.append(formatTransition(from, transition))
            }
        }

        sb.appendLine("@enduml")
        return sb.toString()
    }

    private fun <STATE, EVENT> formatTransition(
        from: STATE,
        transition: ExTransition<STATE, EVENT>
    ): String {
        val fromId = stateId(from)
        val toId = stateId(transition.to.state)

        val label = buildTransitionLabel(transition)

        return if (label.isNotEmpty()) {
            "$fromId --> $toId : $label\n"
        } else {
            "$fromId --> $toId\n"
        }
    }

    private fun <STATE, EVENT> buildTransitionLabel(transition: ExTransition<STATE, EVENT>): String {
        val parts = mutableListOf<String>()

        // Add event
        transition.event?.let {
            parts.add("[$it]")
        }

        // Add conditions (conditions are displayed on arrows)
        transition.to.conditions.forEach { condition ->
            parts.add("[${condition}]")
        }

        // Add timeout
        transition.to.timeout?.let {
            parts.add("⏱${it.value}${it.unit}")
        }

        return parts.joinToString(" ")
    }

    private fun <STATE> stateId(state: STATE): String {
        return state.toString().replace(" ", "_").replace("-", "_")
    }
}