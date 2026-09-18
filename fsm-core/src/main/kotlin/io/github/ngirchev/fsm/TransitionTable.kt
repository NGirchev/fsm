package io.github.ngirchev.fsm

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

interface TransitionTable<STATE, TRANSITION : Transition<STATE>> {
    val transitions: Map<STATE, LinkedHashSet<out TRANSITION>>
    val autoTransitionEnabled: Boolean
        get() = false

    /**
     * get transition by target state.
     */
    fun getTransitionByState(context: StateContext<STATE>, newState: STATE): TRANSITION?

    /**
     * get auto transition for current state.
     * Default implementation bridges to the newer two-argument overload when a concrete
     * implementation overrides it, otherwise returns `null`.
     */
    fun getAutoTransition(
        context: StateContext<STATE>,
    ): TRANSITION? {
        val overrides = transitionTableAutoTransitionOverrides(javaClass)
        val twoArgOverride = overrides.twoArgOverride ?: return null
        @Suppress("UNCHECKED_CAST")
        return invokeAutoTransitionOverride(twoArgOverride, context, autoTransitionEnabled)
    }

    fun getAutoTransition(
        context: StateContext<STATE>,
        autoTransitionEnabled: Boolean = this.autoTransitionEnabled,
    ): TRANSITION? {
        if (!autoTransitionEnabled) {
            return null
        }

        val overrides = transitionTableAutoTransitionOverrides(javaClass)
        val oneArgOverride = overrides.oneArgOverride ?: return null
        @Suppress("UNCHECKED_CAST")
        return invokeAutoTransitionOverride(oneArgOverride, context)
    }

    fun createFsm(initialState: STATE): StateSupport<STATE>
    fun <DOMAIN : StateContext<STATE>> createDomainFsm(): DomainSupport<DOMAIN, STATE>
}

@Suppress("UNCHECKED_CAST")
private fun <TRANSITION> Any.invokeAutoTransitionOverride(
    method: Method,
    vararg arguments: Any?,
): TRANSITION? {
    return try {
        if (!method.canAccess(this) && !method.trySetAccessible()) {
            throw IllegalAccessException("Cannot access auto transition override [$method]")
        }
        method.invoke(this, *arguments) as TRANSITION?
    } catch (exception: InvocationTargetException) {
        throw exception.targetException
    }
}

private data class TransitionTableAutoTransitionOverrides(
    val oneArgOverride: Method?,
    val twoArgOverride: Method?,
)

private val transitionTableAutoTransitionOverridesCache =
    ConcurrentHashMap<Class<*>, TransitionTableAutoTransitionOverrides>()

private fun transitionTableAutoTransitionOverrides(
    implementationClass: Class<*>,
): TransitionTableAutoTransitionOverrides {
    return transitionTableAutoTransitionOverridesCache.computeIfAbsent(implementationClass) { clazz ->
        TransitionTableAutoTransitionOverrides(
            oneArgOverride = findTransitionTableOverride(clazz, StateContext::class.java),
            twoArgOverride = findTransitionTableOverride(
                clazz,
                StateContext::class.java,
                Boolean::class.javaPrimitiveType!!,
            ),
        )
    }
}

private fun findTransitionTableOverride(
    implementationClass: Class<*>,
    vararg parameterTypes: Class<*>,
): Method? {
    return implementationClass.methods.firstOrNull { method ->
        method.name == "getAutoTransition" &&
            method.parameterTypes.contentEquals(parameterTypes) &&
            method.declaringClass != TransitionTable::class.java
    }
}
