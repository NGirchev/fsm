package io.github.ngirchev.fsm.impl

import org.slf4j.LoggerFactory

internal object TransitionTableDiagnostics {
    private val logger = LoggerFactory.getLogger(TransitionTableDiagnostics::class.java)

    fun <STATE, TRANSITION : AbstractTransition<STATE>, KEY> warnOnCatchAllBeforeLaterTransitions(
        transitions: Map<STATE, LinkedHashSet<TRANSITION>>,
        groupKey: (TRANSITION) -> KEY,
        groupLabel: (KEY) -> String,
    ) {
        transitions.forEach { (from, transitionSet) ->
            transitionSet.groupByPreservingOrder(groupKey)
                .forEach { (key, group) ->
                    val catchAllIndex = group.indexOfFirst { it.to.conditions.isEmpty() }
                    if (catchAllIndex != -1 && catchAllIndex < group.lastIndex) {
                        val catchAll = group[catchAllIndex]
                        val hidden = group[catchAllIndex + 1]
                        logger.warn(
                            "Ambiguous transition order from [{}]{}: unguarded transition to [{}] " +
                                "precedes transition to [{}]. The unguarded transition matches first " +
                                "and may hide later transitions.",
                            from,
                            groupLabel(key),
                            catchAll.to.state,
                            hidden.to.state,
                        )
                    }
                }
        }
    }

    private fun <TRANSITION, KEY> Iterable<TRANSITION>.groupByPreservingOrder(
        keySelector: (TRANSITION) -> KEY,
    ): Map<KEY, List<TRANSITION>> {
        val groups = linkedMapOf<KEY, MutableList<TRANSITION>>()
        forEach { transition ->
            groups.getOrPut(keySelector(transition)) { mutableListOf() }.add(transition)
        }
        return groups
    }
}
