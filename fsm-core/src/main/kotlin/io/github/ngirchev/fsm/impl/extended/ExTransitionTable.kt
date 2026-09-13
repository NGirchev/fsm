package io.github.ngirchev.fsm.impl.extended

import io.github.ngirchev.fsm.*
import io.github.ngirchev.fsm.exception.DuplicateTransitionException
import io.github.ngirchev.fsm.exception.FsmException
import io.github.ngirchev.fsm.impl.AbstractTransitionTable
import io.github.ngirchev.fsm.impl.TransitionTableDiagnostics
import kotlin.jvm.JvmSuppressWildcards

fun interface ExTransitionTableFactory<STATE, EVENT, TABLE : ExTransitionTable<STATE, EVENT>> {
    fun create(
        transitions: Map<STATE, @JvmSuppressWildcards LinkedHashSet<ExTransition<STATE, EVENT>>>,
        autoTransitionEnabled: Boolean,
        autoTransitionScheduler: AutoTransitionScheduler<STATE>,
    ): TABLE
}

fun interface ExDomainFsmFactory<
    DOMAIN : StateContext<STATE>,
    STATE,
    EVENT,
    FSM : ExDomainFsm<DOMAIN, STATE, EVENT>,
> {
    fun create(
        transitionTable: ExTransitionTable<STATE, EVENT>,
        autoTransitionEnabled: Boolean,
        autoTransitionScheduler: AutoTransitionScheduler<STATE>,
    ): FSM
}

open class ExTransitionTable<STATE, EVENT>(
    transitions: Map<STATE, LinkedHashSet<ExTransition<STATE, EVENT>>>,
    override var autoTransitionEnabled: Boolean = false,
    protected val autoTransitionScheduler: AutoTransitionScheduler<STATE>,
) : AbstractTransitionTable<STATE, ExTransition<STATE, EVENT>>(transitions, autoTransitionEnabled) {

    internal fun getTransitionByEvent(context: StateContext<STATE>, event: EVENT): ExTransition<STATE, EVENT>? {
        return transitions[context.state]
            ?.filter { matchesEvent(it.event, event) }
            ?.firstOrNull { it.to.conditions.all { condition -> condition.invoke(context) } }
    }

    protected fun matchesEvent(transitionEvent: EVENT?, runtimeEvent: EVENT): Boolean {
        return eventIdentity(transitionEvent) == eventIdentity(runtimeEvent)
    }

    protected open fun eventIdentity(event: EVENT?): Any? {
        return eventTypeOf(event)
    }

    override fun getAutoTransition(
        context: StateContext<STATE>,
    ): ExTransition<STATE, EVENT>? = getAutoTransition(context, autoTransitionEnabled)

    override fun getAutoTransition(
        context: StateContext<STATE>,
        autoTransitionEnabled: Boolean,
    ): ExTransition<STATE, EVENT>? {
        return transitions[context.state]
            ?.firstOrNull {
                it.event == null &&
                    (autoTransitionEnabled || it.to.autoTransitionEnabled) &&
                    it.to.conditions.all { condition -> condition.invoke(context) }
            }
    }

    class Builder<STATE, EVENT> {

        internal val transitions: MutableMap<STATE, LinkedHashSet<ExTransition<STATE, EVENT>>> = hashMapOf()
        private var autoTransitionEnabled: Boolean = false
        private var autoTransitionScheduler: AutoTransitionScheduler<STATE> = ImmediateAutoTransitionScheduler()
        private var maxImmediateAutoTransitions: Int = 0

        fun autoTransitionEnabled(enabled: Boolean): Builder<STATE, EVENT> {
            this.autoTransitionEnabled = enabled
            return this
        }

        fun autoTransitionScheduler(scheduler: AutoTransitionScheduler<STATE>): Builder<STATE, EVENT> {
            this.autoTransitionScheduler = scheduler
            return this
        }

        fun maxImmediateAutoTransitions(limit: Int): Builder<STATE, EVENT> {
            require(limit >= 0) { "maxImmediateAutoTransitions must not be negative" }
            maxImmediateAutoTransitions = limit
            return this
        }

        fun add(
            from: STATE,
            onEvent: EVENT? = null,
            to: STATE,
            condition: Guard<in StateContext<STATE>>? = null,
            action: Action<in StateContext<STATE>>? = null,
            postAction: Action<in StateContext<STATE>>? = null,
            timeout: Timeout? = null,
            autoTransitionScheduler: AutoTransitionScheduler<STATE>? = null,
            autoTransitionEnabled: Boolean = false,
        ): Builder<STATE, EVENT> {
            requireAutoTransitionSettingsCanBeUsed(onEvent, autoTransitionScheduler, autoTransitionEnabled)
            transitions.getOrPut(from) { LinkedHashSet() }
                .also { transitionSet ->
                    val transition = ExTransition(
                        from,
                        To(to, condition, action, postAction, timeout, autoTransitionScheduler, autoTransitionEnabled),
                        onEvent,
                    )
                    if (!transitionSet.add(transition)) {
                        throw DuplicateTransitionException(transition)
                    }
                }
            return this
        }

        fun add(vararg transition: ExTransition<STATE, EVENT>): Builder<STATE, EVENT> {
            for (t in transition) {
                requireAutoTransitionSettingsCanBeUsed(t.event, t.to.autoTransitionScheduler, t.to.autoTransitionEnabled)
                transitions.putIfAbsent(t.from, LinkedHashSet())
                if (!transitions[t.from]!!.add(t)) {
                    throw DuplicateTransitionException(t)
                }
            }
            return this
        }

        fun add(from: STATE, onEvent: EVENT? = null, vararg to: To<STATE>): Builder<STATE, EVENT> {
            for (t in to) {
                requireAutoTransitionSettingsCanBeUsed(onEvent, t.autoTransitionScheduler, t.autoTransitionEnabled)
                transitions.getOrPut(from) { LinkedHashSet() }
                    .also { transitionSet ->
                        val transition = ExTransition(
                            from,
                            To(
                                state = t.state,
                                conditions = t.conditions.toList(),
                                actions = t.actions.toList(),
                                postActions = t.postActions.toList(),
                                timeout = t.timeout,
                                autoTransitionScheduler = t.autoTransitionScheduler,
                                autoTransitionEnabled = t.autoTransitionEnabled,
                            ),
                            onEvent,
                        )
                        if (!transitionSet.add(transition)) {
                            throw DuplicateTransitionException(transition)
                        }
                    }
            }
            return this
        }

        fun from(from: STATE): FromBuilder<STATE, EVENT> {
            return FromBuilder(from, this)
        }

        private fun requireAutoTransitionSettingsCanBeUsed(
            event: EVENT?,
            scheduler: AutoTransitionScheduler<STATE>?,
            localAutoTransitionEnabled: Boolean,
        ) {
            if (event != null && (scheduler != null || localAutoTransitionEnabled)) {
                throw FsmException("Auto transition settings can only be configured for eventless transitions")
            }
        }

        fun build(): ExTransitionTable<STATE, EVENT> {
            val snapshot = snapshotTransitions()
            return ExTransitionTable(snapshot, autoTransitionEnabled, autoTransitionScheduler)
                .also {
                    it.maxImmediateAutoTransitions = maxImmediateAutoTransitions
                    it.warnOnAmbiguousTransitions()
                }
        }

        fun <TABLE : ExTransitionTable<STATE, EVENT>> build(
            factory: ExTransitionTableFactory<STATE, EVENT, TABLE>,
        ): TABLE {
            val snapshot = snapshotTransitions()
            return factory.create(snapshot, autoTransitionEnabled, autoTransitionScheduler)
                .also {
                    it.maxImmediateAutoTransitions = maxImmediateAutoTransitions
                    it.warnOnAmbiguousTransitions()
                }
        }

        private fun snapshotTransitions(): Map<STATE, LinkedHashSet<ExTransition<STATE, EVENT>>> {
            // Keep insertion order: auto/event transitions use the first matching transition.
            return transitions.mapValues { (_, transitions) -> LinkedHashSet(transitions) }
        }
    }

    internal fun warnOnAmbiguousTransitions() {
        @Suppress("UNCHECKED_CAST")
        val transitionSnapshot = transitions as Map<STATE, LinkedHashSet<ExTransition<STATE, EVENT>>>
        TransitionTableDiagnostics.warnOnCatchAllBeforeLaterTransitions(
            transitionSnapshot,
            groupKey = { eventIdentity(it.event) },
            groupLabel = { eventType ->
                if (eventType == null) " for auto transitions" else " for event [$eventType]"
            },
        )
    }

    override fun createFsm(initialState: STATE): ExFsm<STATE, EVENT> {
        return ExFsm(initialState, this, autoTransitionEnabled, autoTransitionScheduler)
    }

    override fun <DOMAIN : StateContext<STATE>> createDomainFsm(): ExDomainFsm<DOMAIN, STATE, EVENT> {
        return ExDomainFsm(this, autoTransitionEnabled, autoTransitionScheduler)
    }

    fun <DOMAIN : StateContext<STATE>, FSM : ExDomainFsm<DOMAIN, STATE, EVENT>> createDomainFsm(
        factory: ExDomainFsmFactory<DOMAIN, STATE, EVENT, FSM>,
    ): FSM {
        return factory.create(this, autoTransitionEnabled, autoTransitionScheduler)
    }
}

class FromBuilder<STATE, EVENT>(
    private val from: STATE,
    private val rootBuilder: ExTransitionTable.Builder<STATE, EVENT>
) {
    private var event: EVENT? = null

    fun onEvent(event: EVENT): FromBuilder<STATE, EVENT> {
        if (this.event != null) {
            throw FsmException("Already has event")
        }
        this.event = event
        return this
    }

    fun to(to: STATE): ToBuilder<STATE, EVENT> {
        return ToBuilder(from, to, rootBuilder, event)
    }

    fun toMultiple(): ToMultipleBuilder<STATE, EVENT> {
        return ToMultipleBuilder(from, rootBuilder, event)
    }
}

class EventFromBuilder<STATE, EVENT> internal constructor(
    private val from: STATE,
    private val rootBuilder: ExTransitionTable.Builder<STATE, EVENT>,
    private val event: EVENT,
) {
    fun to(to: STATE): EventToBuilder<STATE, EVENT> {
        return EventToBuilder(ToBuilder(from, to, rootBuilder, event))
    }
}

class ToBuilder<STATE, EVENT>(
    private val from: STATE,
    private val to: STATE,
    private val rootBuilder: ExTransitionTable.Builder<STATE, EVENT>,
    private var event: EVENT? = null
) {
    private val conditions: MutableList<Guard<in StateContext<STATE>>> = mutableListOf()
    private val actions: MutableList<Action<in StateContext<STATE>>> = mutableListOf()
    private val postActions: MutableList<Action<in StateContext<STATE>>> = mutableListOf()
    private var timeout: Timeout? = null
    private var autoTransitionScheduler: AutoTransitionScheduler<STATE>? = null
    private var autoTransitionEnabled: Boolean = false

    fun onEvent(event: EVENT): ToBuilder<STATE, EVENT> {
        if (this.event != null) {
            throw FsmException("Already has event")
        }
        this.event = event
        return this
    }

    fun onCondition(condition: Guard<in StateContext<STATE>>): ToBuilder<STATE, EVENT> {
        this.conditions.add(condition)
        return this
    }

    fun action(action: Action<in StateContext<STATE>>): ToBuilder<STATE, EVENT> {
        this.actions.add(action)
        return this
    }

    fun postAction(postAction: Action<in StateContext<STATE>>): ToBuilder<STATE, EVENT> {
        this.postActions.add(postAction)
        return this
    }

    fun auto(): AutoToBuilder<STATE, EVENT> {
        requireAutoTransition()
        this.autoTransitionEnabled = true
        return AutoToBuilder(this)
    }

    internal fun deferWithForAuto(scheduler: AutoTransitionScheduler<STATE>): ToBuilder<STATE, EVENT> {
        requireAutoTransition()
        this.autoTransitionScheduler = scheduler
        return this
    }

    fun timeout(timeout: Timeout): ToBuilder<STATE, EVENT> {
        if (this.timeout != null) {
            throw FsmException("Already has timeout")
        }
        this.timeout = timeout
        return this
    }

    fun end(): ExTransitionTable.Builder<STATE, EVENT> {
        return rootBuilder.add(
            ExTransition(
                from,
                To(
                    to,
                    conditions.toList(),
                    actions.toList(),
                    postActions.toList(),
                    timeout,
                    autoTransitionScheduler,
                    autoTransitionEnabled,
                ),
                event,
            )
        )
    }

    private fun requireAutoTransition() {
        if (event != null) {
            throw FsmException("Only eventless auto transitions can be configured as auto")
        }
    }
}

class EventToBuilder<STATE, EVENT> internal constructor(
    private val delegate: ToBuilder<STATE, EVENT>
) {
    fun onCondition(condition: Guard<in StateContext<STATE>>): EventToBuilder<STATE, EVENT> {
        delegate.onCondition(condition)
        return this
    }

    fun action(action: Action<in StateContext<STATE>>): EventToBuilder<STATE, EVENT> {
        delegate.action(action)
        return this
    }

    fun postAction(postAction: Action<in StateContext<STATE>>): EventToBuilder<STATE, EVENT> {
        delegate.postAction(postAction)
        return this
    }

    fun timeout(timeout: Timeout): EventToBuilder<STATE, EVENT> {
        delegate.timeout(timeout)
        return this
    }

    fun end(): ExTransitionTable.Builder<STATE, EVENT> {
        return delegate.end()
    }
}

class AutoToBuilder<STATE, EVENT> internal constructor(
    private val delegate: ToBuilder<STATE, EVENT>
) {
    fun onCondition(condition: Guard<in StateContext<STATE>>): AutoToBuilder<STATE, EVENT> {
        delegate.onCondition(condition)
        return this
    }

    fun action(action: Action<in StateContext<STATE>>): AutoToBuilder<STATE, EVENT> {
        delegate.action(action)
        return this
    }

    fun postAction(postAction: Action<in StateContext<STATE>>): AutoToBuilder<STATE, EVENT> {
        delegate.postAction(postAction)
        return this
    }

    fun deferWith(scheduler: AutoTransitionScheduler<STATE>): AutoToBuilder<STATE, EVENT> {
        delegate.deferWithForAuto(scheduler)
        return this
    }

    fun timeout(timeout: Timeout): AutoToBuilder<STATE, EVENT> {
        delegate.timeout(timeout)
        return this
    }

    fun end(): ExTransitionTable.Builder<STATE, EVENT> {
        return delegate.end()
    }
}

class ToMultipleBuilder<STATE, EVENT>(
    private val from: STATE,
    private val rootBuilder: ExTransitionTable.Builder<STATE, EVENT>,
    private var event: EVENT? = null
) {

    private val transitions: ArrayList<ExTransition<STATE, EVENT>> = ArrayList()
    internal fun addTransition(transition: ExTransition<STATE, EVENT>): ToMultipleBuilder<STATE, EVENT> {
        transitions.add(transition)
        return this
    }

    fun to(to: STATE): ToMultipleTransitionBuilder<STATE, EVENT> {
        return ToMultipleTransitionBuilder(from, to, this, event)
    }

    internal fun to(to: STATE, event: EVENT): ToMultipleTransitionBuilder<STATE, EVENT> {
        return ToMultipleTransitionBuilder(from, to, this, event)
    }

    fun onEvent(event: EVENT): EventToMultipleBuilder<STATE, EVENT> {
        return EventToMultipleBuilder(this, event)
    }

    fun endMultiple(): ExTransitionTable.Builder<STATE, EVENT> {
        for (t in transitions) {
            rootBuilder.add(t)
        }
        return rootBuilder
    }
}

class ToMultipleTransitionBuilder<STATE, EVENT>(
    private val from: STATE,
    private val to: STATE,
    private val multipleBuilder: ToMultipleBuilder<STATE, EVENT>,
    private var event: EVENT? = null
) {
    private val conditions: MutableList<Guard<in StateContext<STATE>>> = mutableListOf()
    private val actions: MutableList<Action<in StateContext<STATE>>> = mutableListOf()
    private val postActions: MutableList<Action<in StateContext<STATE>>> = mutableListOf()
    private var timeout: Timeout? = null
    private var autoTransitionScheduler: AutoTransitionScheduler<STATE>? = null
    private var autoTransitionEnabled: Boolean = false

    fun onEvent(event: EVENT): ToMultipleTransitionBuilder<STATE, EVENT> {
        if (this.event != null) {
            throw FsmException("Already has event")
        }
        this.event = event
        return this
    }

    fun onCondition(condition: Guard<in StateContext<STATE>>): ToMultipleTransitionBuilder<STATE, EVENT> {
        this.conditions.add(condition)
        return this
    }

    fun action(action: Action<in StateContext<STATE>>): ToMultipleTransitionBuilder<STATE, EVENT> {
        this.actions.add(action)
        return this
    }

    fun postAction(postAction: Action<in StateContext<STATE>>): ToMultipleTransitionBuilder<STATE, EVENT> {
        this.postActions.add(postAction)
        return this
    }

    fun auto(): AutoToMultipleTransitionBuilder<STATE, EVENT> {
        requireAutoTransition()
        this.autoTransitionEnabled = true
        return AutoToMultipleTransitionBuilder(this)
    }

    internal fun deferWithForAuto(scheduler: AutoTransitionScheduler<STATE>): ToMultipleTransitionBuilder<STATE, EVENT> {
        requireAutoTransition()
        this.autoTransitionScheduler = scheduler
        return this
    }

    fun timeout(timeout: Timeout): ToMultipleTransitionBuilder<STATE, EVENT> {
        if (this.timeout != null) {
            throw FsmException("Already has timeout")
        }
        this.timeout = timeout
        return this
    }

    fun end(): ToMultipleBuilder<STATE, EVENT> {
        return multipleBuilder.addTransition(
            ExTransition(
                from,
                To(
                    to,
                    conditions.toList(),
                    actions.toList(),
                    postActions.toList(),
                    timeout,
                    autoTransitionScheduler,
                    autoTransitionEnabled,
                ),
                event,
            )
        )
    }

    private fun requireAutoTransition() {
        if (event != null) {
            throw FsmException("Only eventless auto transitions can be configured as auto")
        }
    }
}

class EventToMultipleBuilder<STATE, EVENT> internal constructor(
    private val delegate: ToMultipleBuilder<STATE, EVENT>,
    private val event: EVENT,
) {
    fun to(to: STATE): EventToMultipleTransitionBuilder<STATE, EVENT> {
        return EventToMultipleTransitionBuilder(delegate.to(to, event))
    }

    fun endMultiple(): ExTransitionTable.Builder<STATE, EVENT> {
        return delegate.endMultiple()
    }
}

class EventToMultipleTransitionBuilder<STATE, EVENT> internal constructor(
    private val delegate: ToMultipleTransitionBuilder<STATE, EVENT>
) {
    fun onCondition(condition: Guard<in StateContext<STATE>>): EventToMultipleTransitionBuilder<STATE, EVENT> {
        delegate.onCondition(condition)
        return this
    }

    fun action(action: Action<in StateContext<STATE>>): EventToMultipleTransitionBuilder<STATE, EVENT> {
        delegate.action(action)
        return this
    }

    fun postAction(postAction: Action<in StateContext<STATE>>): EventToMultipleTransitionBuilder<STATE, EVENT> {
        delegate.postAction(postAction)
        return this
    }

    fun timeout(timeout: Timeout): EventToMultipleTransitionBuilder<STATE, EVENT> {
        delegate.timeout(timeout)
        return this
    }

    fun end(): ToMultipleBuilder<STATE, EVENT> {
        return delegate.end()
    }
}

class AutoToMultipleTransitionBuilder<STATE, EVENT> internal constructor(
    private val delegate: ToMultipleTransitionBuilder<STATE, EVENT>
) {
    fun onCondition(condition: Guard<in StateContext<STATE>>): AutoToMultipleTransitionBuilder<STATE, EVENT> {
        delegate.onCondition(condition)
        return this
    }

    fun action(action: Action<in StateContext<STATE>>): AutoToMultipleTransitionBuilder<STATE, EVENT> {
        delegate.action(action)
        return this
    }

    fun postAction(postAction: Action<in StateContext<STATE>>): AutoToMultipleTransitionBuilder<STATE, EVENT> {
        delegate.postAction(postAction)
        return this
    }

    fun deferWith(scheduler: AutoTransitionScheduler<STATE>): AutoToMultipleTransitionBuilder<STATE, EVENT> {
        delegate.deferWithForAuto(scheduler)
        return this
    }

    fun timeout(timeout: Timeout): AutoToMultipleTransitionBuilder<STATE, EVENT> {
        delegate.timeout(timeout)
        return this
    }

    fun end(): ToMultipleBuilder<STATE, EVENT> {
        return delegate.end()
    }
}
