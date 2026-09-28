package consumer

import io.github.ngirchev.fsm.DomainSupport
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.StateSupport
import io.github.ngirchev.fsm.Transition
import io.github.ngirchev.fsm.TransitionTable
import io.github.ngirchev.fsm.impl.basic.BTransition
import kotlin.test.Test
import kotlin.test.assertEquals

class PrivateTransitionTableCompatibilityTest {
    @Test
    fun bridgeCanInvokePrivateLegacyImplementationInConsumerPackage() {
        assertEquals("to", LegacyTable().getAutoTransition(Context(), true)?.to?.state)
    }

    @Test
    fun bridgeCanInvokePrivateNewImplementationInConsumerPackage() {
        assertEquals("to", NewTable().getAutoTransition(Context())?.to?.state)
    }

    private abstract class Table : TransitionTable<String, BTransition<String>> {
        override val transitions: Map<String, LinkedHashSet<out BTransition<String>>> = emptyMap()
        override val autoTransitionEnabled: Boolean = true
        override fun getTransitionByState(context: StateContext<String>, newState: String): BTransition<String>? = null
        override fun createFsm(initialState: String): StateSupport<String> = error("Unused")
        override fun <DOMAIN : StateContext<String>> createDomainFsm(): DomainSupport<DOMAIN, String> = error("Unused")
    }

    private class LegacyTable : Table() {
        override fun getAutoTransition(context: StateContext<String>): BTransition<String> = BTransition("from", "to")
    }

    private class NewTable : Table() {
        override fun getAutoTransition(context: StateContext<String>, autoTransitionEnabled: Boolean): BTransition<String>? =
            if (autoTransitionEnabled) BTransition("from", "to") else null
    }

    private class Context : StateContext<String> {
        override var state: String = "from"
        override var currentTransition: Transition<String>? = null
    }
}
