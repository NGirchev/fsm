package io.github.ngirchev.fsm.spring

import io.github.ngirchev.fsm.Action
import io.github.ngirchev.fsm.AutoTransitionScheduler
import io.github.ngirchev.fsm.Guard
import io.github.ngirchev.fsm.StateContext

/** Bean names are the stable IDs stored in JSON. Context types must match the loaded table. */
class FsmBeanRegistry(
    private val actions: Map<String, Action<*>>,
    private val guards: Map<String, Guard<*>>,
    private val schedulers: Map<String, AutoTransitionScheduler<*>>,
) {
    constructor(actions: Map<String, Action<*>>, guards: Map<String, Guard<*>>) :
        this(actions, guards, emptyMap())

    fun hasAction(name: String): Boolean = name in actions
    fun hasGuard(name: String): Boolean = name in guards

    @Suppress("UNCHECKED_CAST")
    fun <STATE> action(name: String): Action<StateContext<STATE>> =
        requireNotNull(actions[name]) { "Unknown action bean: $name" } as Action<StateContext<STATE>>

    @Suppress("UNCHECKED_CAST")
    fun <STATE> guard(name: String): Guard<StateContext<STATE>> =
        requireNotNull(guards[name]) { "Unknown guard bean: $name" } as Guard<StateContext<STATE>>

    @Suppress("UNCHECKED_CAST")
    fun <STATE> scheduler(name: String): AutoTransitionScheduler<STATE> =
        requireNotNull(schedulers[name]) { "Unknown scheduler bean: $name" } as AutoTransitionScheduler<STATE>

    fun actionName(action: Action<*>): String = nameOf(action, actions, "action")
    fun guardName(guard: Guard<*>): String = nameOf(guard, guards, "guard")
    fun schedulerName(scheduler: AutoTransitionScheduler<*>): String = nameOf(scheduler, schedulers, "scheduler")

    private fun nameOf(bean: Any, beans: Map<String, *>, kind: String): String {
        val names = beans.filterValues { it === bean }.keys
        require(names.size == 1) {
            "Expected exactly one registered $kind bean name; found: $names"
        }
        return names.single()
    }
}
