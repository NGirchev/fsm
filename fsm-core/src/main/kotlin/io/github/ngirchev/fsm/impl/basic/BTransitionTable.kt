package io.github.ngirchev.fsm.impl.basic

import io.github.ngirchev.fsm.Action
import io.github.ngirchev.fsm.AutoTransitionScheduler
import io.github.ngirchev.fsm.Guard
import io.github.ngirchev.fsm.ImmediateAutoTransitionScheduler
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.To
import io.github.ngirchev.fsm.exception.DuplicateTransitionException
import io.github.ngirchev.fsm.impl.AbstractTransitionTable
import io.github.ngirchev.fsm.impl.TransitionTableDiagnostics
import kotlin.jvm.JvmSuppressWildcards

fun interface BTransitionTableFactory<STATE, TABLE : BTransitionTable<STATE>> {
    fun create(
        transitions: Map<STATE, @JvmSuppressWildcards LinkedHashSet<BTransition<STATE>>>,
        autoTransitionEnabled: Boolean,
        autoTransitionScheduler: AutoTransitionScheduler<STATE>,
    ): TABLE
}

fun interface BDomainFsmFactory<DOMAIN : StateContext<STATE>, STATE, FSM : BDomainFsm<DOMAIN, STATE>> {
    fun create(
        transitionTable: BTransitionTable<STATE>,
        autoTransitionEnabled: Boolean,
        autoTransitionScheduler: AutoTransitionScheduler<STATE>,
    ): FSM
}

open class BTransitionTable<STATE>(
    transitions: Map<STATE, LinkedHashSet<BTransition<STATE>>>,
    override var autoTransitionEnabled: Boolean,
    protected val autoTransitionScheduler: AutoTransitionScheduler<STATE>,
) : AbstractTransitionTable<STATE, BTransition<STATE>>(transitions, autoTransitionEnabled) {

    class Builder<STATE> {

        private val transitions: MutableMap<STATE, LinkedHashSet<BTransition<STATE>>> = hashMapOf()
        private var autoTransitionEnabled: Boolean = false
        private var autoTransitionScheduler: AutoTransitionScheduler<STATE> = ImmediateAutoTransitionScheduler()

        /**
         * Placeholder for symmetry with extended FSM DSL.
         * For basic FSM auto transitions are controlled via `BFsm` constructor parameter.
         */
        fun autoTransitionEnabled(enabled: Boolean): Builder<STATE> {
            this.autoTransitionEnabled = enabled
            return this
        }

        fun autoTransitionScheduler(scheduler: AutoTransitionScheduler<STATE>): Builder<STATE> {
            this.autoTransitionScheduler = scheduler
            return this
        }

        /**
         * Simplified add for state-only transitions (no conditions, actions, or scheduler).
         * To attach a per-transition [AutoTransitionScheduler], use the [To]-object overload
         * [add] or the fluent DSL ([FromBuilder] / [ToBuilder] with `scheduleWith`).
         */
        fun add(from: STATE, vararg to: STATE): Builder<STATE> {
            val list: List<BTransition<STATE>> = to.map { BTransition(from, To(it)) }
            transitions.putIfAbsent(from, LinkedHashSet())
            for (t in list) {
                if (!transitions[from]!!.add(t)) {
                    throw DuplicateTransitionException(t)
                }
            }
            return this
        }

        fun add(vararg transition: BTransition<STATE>): Builder<STATE> {
            for (t in transition) {
                transitions.putIfAbsent(t.from, LinkedHashSet())
                if (!transitions[t.from]!!.add(t)) {
                    throw DuplicateTransitionException(t)
                }
            }
            return this
        }

        fun add(from: STATE, vararg to: To<STATE>): Builder<STATE> {
            for (t in to) {
                transitions.putIfAbsent(from, LinkedHashSet())
                val transition = BTransition(
                    from,
                    To(
                        state = t.state,
                        conditions = t.conditions.toList(),
                        actions = t.actions.toList(),
                        postActions = t.postActions.toList(),
                        timeout = t.timeout,
                        autoTransitionScheduler = t.autoTransitionScheduler,
                    )
                )
                if (!transitions[from]!!.add(transition)) {
                    throw DuplicateTransitionException(transition)
                }
            }
            return this
        }

        fun from(from: STATE): FromBuilder<STATE> {
            return FromBuilder(from, this)
        }

        fun build(): BTransitionTable<STATE> {
            val snapshot = snapshotTransitions()
            warnOnAmbiguousTransitions(snapshot)
            return BTransitionTable(snapshot, autoTransitionEnabled, autoTransitionScheduler)
        }

        fun <TABLE : BTransitionTable<STATE>> build(
            factory: BTransitionTableFactory<STATE, TABLE>,
        ): TABLE {
            val snapshot = snapshotTransitions()
            warnOnAmbiguousTransitions(snapshot)
            return factory.create(snapshot, autoTransitionEnabled, autoTransitionScheduler)
        }

        private fun warnOnAmbiguousTransitions(
            snapshot: Map<STATE, LinkedHashSet<BTransition<STATE>>>,
        ) {
            TransitionTableDiagnostics.warnOnCatchAllBeforeLaterTransitions(
                snapshot,
                groupKey = { Unit },
                groupLabel = { "" },
            )
        }

        private fun snapshotTransitions(): Map<STATE, LinkedHashSet<BTransition<STATE>>> {
            // Keep insertion order: auto transitions use the first matching transition.
            return transitions.mapValues { (_, transitions) -> LinkedHashSet(transitions) }
        }
    }

    override fun createFsm(initialState: STATE): BFsm<STATE> {
        return BFsm(initialState, this, autoTransitionEnabled, autoTransitionScheduler)
    }

    override fun <DOMAIN : StateContext<STATE>> createDomainFsm(): BDomainFsm<DOMAIN, STATE> {
        return BDomainFsm(this, autoTransitionEnabled, autoTransitionScheduler)
    }

    fun <DOMAIN : StateContext<STATE>, FSM : BDomainFsm<DOMAIN, STATE>> createDomainFsm(
        factory: BDomainFsmFactory<DOMAIN, STATE, FSM>,
    ): FSM {
        return factory.create(this, autoTransitionEnabled, autoTransitionScheduler)
    }

    override fun getAutoTransition(context: StateContext<STATE>): BTransition<STATE>? {
        return transitions[context.state]
            ?.firstOrNull {
                it.to.conditions.all { condition -> condition.invoke(context) }
            }
    }
}

class FromBuilder<STATE>(
    private val from: STATE,
    private val rootBuilder: BTransitionTable.Builder<STATE>
) {
    fun to(to: STATE): ToBuilder<STATE> {
        return ToBuilder(from, to, rootBuilder)
    }

    fun toMultiple(): ToMultipleBuilder<STATE> {
        return ToMultipleBuilder(from, rootBuilder)
    }
}

class ToBuilder<STATE>(
    private val from: STATE,
    private val to: STATE,
    private val rootBuilder: BTransitionTable.Builder<STATE>
) {
    private val conditions: MutableList<Guard<in StateContext<STATE>>> = mutableListOf()
    private val actions: MutableList<Action<in StateContext<STATE>>> = mutableListOf()
    private val postActions: MutableList<Action<in StateContext<STATE>>> = mutableListOf()
    private var autoTransitionScheduler: AutoTransitionScheduler<STATE>? = null

    fun condition(condition: Guard<in StateContext<STATE>>): ToBuilder<STATE> {
        this.conditions.add(condition)
        return this
    }

    fun action(action: Action<in StateContext<STATE>>): ToBuilder<STATE> {
        this.actions.add(action)
        return this
    }

    fun postAction(postAction: Action<in StateContext<STATE>>): ToBuilder<STATE> {
        this.postActions.add(postAction)
        return this
    }

    fun scheduleWith(scheduler: AutoTransitionScheduler<STATE>): ToBuilder<STATE> {
        this.autoTransitionScheduler = scheduler
        return this
    }

    fun end(): BTransitionTable.Builder<STATE> {
        return rootBuilder.add(
            BTransition(
                from,
                To(
                    to,
                    conditions.toList(),
                    actions.toList(),
                    postActions.toList(),
                    autoTransitionScheduler = autoTransitionScheduler,
                ),
            )
        )
    }
}

class ToMultipleBuilder<STATE>(
    private val from: STATE,
    private val rootBuilder: BTransitionTable.Builder<STATE>
) {

    private val transitions: ArrayList<BTransition<STATE>> = ArrayList()
    internal fun addTransition(transition: BTransition<STATE>): ToMultipleBuilder<STATE> {
        transitions.add(transition)
        return this
    }

    fun to(to: STATE): ToMultipleTransitionBuilder<STATE> {
        return ToMultipleTransitionBuilder(from, to, this)
    }

    fun endMultiple(): BTransitionTable.Builder<STATE> {
        for (t in transitions) {
            rootBuilder.add(t)
        }
        return rootBuilder
    }
}

class ToMultipleTransitionBuilder<STATE>(
    private val from: STATE,
    private val to: STATE,
    private val multipleBuilder: ToMultipleBuilder<STATE>
) {
    private val conditions: MutableList<Guard<in StateContext<STATE>>> = mutableListOf()
    private val actions: MutableList<Action<in StateContext<STATE>>> = mutableListOf()
    private val postActions: MutableList<Action<in StateContext<STATE>>> = mutableListOf()
    private var autoTransitionScheduler: AutoTransitionScheduler<STATE>? = null

    fun condition(condition: Guard<in StateContext<STATE>>): ToMultipleTransitionBuilder<STATE> {
        this.conditions.add(condition)
        return this
    }

    fun action(action: Action<in StateContext<STATE>>): ToMultipleTransitionBuilder<STATE> {
        this.actions.add(action)
        return this
    }

    fun postAction(postAction: Action<in StateContext<STATE>>): ToMultipleTransitionBuilder<STATE> {
        this.postActions.add(postAction)
        return this
    }

    fun scheduleWith(scheduler: AutoTransitionScheduler<STATE>): ToMultipleTransitionBuilder<STATE> {
        this.autoTransitionScheduler = scheduler
        return this
    }

    fun end(): ToMultipleBuilder<STATE> {
        return multipleBuilder.addTransition(
            BTransition(
                from,
                To(
                    to,
                    conditions.toList(),
                    actions.toList(),
                    postActions.toList(),
                    autoTransitionScheduler = autoTransitionScheduler,
                )
            )
        )
    }
}
