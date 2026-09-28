package io.github.ngirchev.fsm

import io.github.ngirchev.fsm.impl.basic.BTransition
import io.github.ngirchev.fsm.impl.basic.BTransitionTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TransitionTableCompatibilityTest {

    @Test
    fun legacyImplementationsCanStillOverrideOldAutoTransitionMethodOnly() {
        val table = LegacyTransitionTable()
        val context = TestStateContext("from")

        val transition = table.getAutoTransition(context)

        requireNotNull(transition)
        assertEquals("to", transition.to.state)
        assertEquals(false, table.autoTransitionEnabled)
    }

    @Test
    fun oldSingleArgumentCallStillWorksForBuiltInTransitionTables() {
        val table = BTransitionTable.Builder<String>()
            .autoTransitionEnabled(true)
            .add("from", To("to"))
            .build()
        val context = TestStateContext("from")

        val transition = table.getAutoTransition(context)

        requireNotNull(transition)
        assertEquals("to", transition.to.state)
    }

    @Test
    fun newTwoArgumentCallDelegatesToLegacyOverrideWhenEnabled() {
        val table = LegacyTransitionTable()
        val context = TestStateContext("from")

        val transition = table.getAutoTransition(context, true)

        requireNotNull(transition)
        assertEquals("to", transition.to.state)
    }

    @Test
    fun newTwoArgumentCallPreservesExceptionFromLegacyOverride() {
        val table = ThrowingLegacyTransitionTable()
        val context = TestStateContext("from")

        val error = assertFailsWith<IllegalStateException> {
            table.getAutoTransition(context, true)
        }

        assertEquals("guard failed", error.message)
    }

    @Test
    fun newTwoArgumentCallStillDisablesLegacyAutoTransitionsWhenFlagIsFalse() {
        val table = LegacyTransitionTable()
        val context = TestStateContext("from")

        val transition = table.getAutoTransition(context, false)

        assertNull(transition)
    }

    @Test
    fun newStyleImplementationsCanOverrideTwoArgumentMethodOnly() {
        val table = NewStyleTransitionTable()
        val context = TestStateContext("from")

        val singleArgTransition = table.getAutoTransition(context)
        val enabledTransition = table.getAutoTransition(context, true)
        val disabledTransition = table.getAutoTransition(context, false)

        requireNotNull(singleArgTransition)
        requireNotNull(enabledTransition)
        assertEquals("to", singleArgTransition.to.state)
        assertEquals("to", enabledTransition.to.state)
        assertNull(disabledTransition)
    }

    @Test
    fun defaultImplementationsWithoutOverridesShouldReturnNullWithoutRecursion() {
        val table = NoOverrideTransitionTable()
        val context = TestStateContext("from")

        assertNull(table.getAutoTransition(context))
        assertNull(table.getAutoTransition(context, true))
        assertNull(table.getAutoTransition(context, false))
    }

    private class LegacyTransitionTable : TransitionTable<String, BTransition<String>> {
        override val transitions: Map<String, LinkedHashSet<out BTransition<String>>> = emptyMap()

        override fun getTransitionByState(
            context: StateContext<String>,
            newState: String,
        ): BTransition<String>? = null

        override fun getAutoTransition(
            context: StateContext<String>,
        ): BTransition<String>? = BTransition("from", "to")

        override fun createFsm(initialState: String): StateSupport<String> {
            error("Not needed for compatibility test")
        }

        override fun <DOMAIN : StateContext<String>> createDomainFsm(): DomainSupport<DOMAIN, String> {
            error("Not needed for compatibility test")
        }
    }

    private class NewStyleTransitionTable : TransitionTable<String, BTransition<String>> {
        override val transitions: Map<String, LinkedHashSet<out BTransition<String>>> = emptyMap()
        override val autoTransitionEnabled: Boolean = true

        override fun getTransitionByState(
            context: StateContext<String>,
            newState: String,
        ): BTransition<String>? = null

        override fun getAutoTransition(
            context: StateContext<String>,
            autoTransitionEnabled: Boolean,
        ): BTransition<String>? = if (autoTransitionEnabled) BTransition("from", "to") else null

        override fun createFsm(initialState: String): StateSupport<String> {
            error("Not needed for compatibility test")
        }

        override fun <DOMAIN : StateContext<String>> createDomainFsm(): DomainSupport<DOMAIN, String> {
            error("Not needed for compatibility test")
        }
    }

    private class ThrowingLegacyTransitionTable : TransitionTable<String, BTransition<String>> {
        override val transitions: Map<String, LinkedHashSet<out BTransition<String>>> = emptyMap()

        override fun getTransitionByState(
            context: StateContext<String>,
            newState: String,
        ): BTransition<String>? = null

        override fun getAutoTransition(context: StateContext<String>): BTransition<String>? {
            throw IllegalStateException("guard failed")
        }

        override fun createFsm(initialState: String): StateSupport<String> = error("Not needed for compatibility test")

        override fun <DOMAIN : StateContext<String>> createDomainFsm(): DomainSupport<DOMAIN, String> =
            error("Not needed for compatibility test")
    }

    private class NoOverrideTransitionTable : TransitionTable<String, BTransition<String>> {
        override val transitions: Map<String, LinkedHashSet<out BTransition<String>>> = emptyMap()

        override fun getTransitionByState(
            context: StateContext<String>,
            newState: String,
        ): BTransition<String>? = null

        override fun createFsm(initialState: String): StateSupport<String> {
            error("Not needed for compatibility test")
        }

        override fun <DOMAIN : StateContext<String>> createDomainFsm(): DomainSupport<DOMAIN, String> {
            error("Not needed for compatibility test")
        }
    }

    private class TestStateContext(
        override var state: String,
        override var currentTransition: Transition<String>? = null,
    ) : StateContext<String>
}
