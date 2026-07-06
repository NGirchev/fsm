package io.github.ngirchev.fsm

import io.github.ngirchev.fsm.impl.basic.BTransition
import io.github.ngirchev.fsm.impl.basic.BTransitionTable
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun newTwoArgumentCallFallsBackToSafeDefaultForLegacyImplementations() {
        val table = LegacyTransitionTable()
        val context = TestStateContext("from")

        val transition = table.getAutoTransition(context, true)

        assertNull(transition)
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

    private class TestStateContext(
        override var state: String,
        override var currentTransition: Transition<String>? = null,
    ) : StateContext<String>
}
