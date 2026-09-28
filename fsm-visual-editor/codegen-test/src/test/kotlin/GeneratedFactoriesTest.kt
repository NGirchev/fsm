import generated.java_builder
import generated.java_fluent
import generated.kotlin_builder
import generated.kotlin_fluent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class GeneratedFactoriesTest {
    @Test
    fun javaFluent() {
        val order = java_fluent.Order(java_fluent.State.NEW)
        val fsm = java_fluent.create().getFsmForDomain(order)
        fsm.startAutoTransitions()
        assertEquals(java_fluent.State.READY, order.state)
        fsm.onEvent(java_fluent.Event.FINISH)
        assertEquals(java_fluent.State.DONE, order.state)
        assertFails { fsm.onEvent(java_fluent.Event.FINISH) }
    }

    @Test
    fun javaBuilder() {
        val order = java_builder.Order(java_builder.State.NEW)
        val fsm = java_builder.create().getFsmForDomain(order)
        fsm.startAutoTransitions()
        assertEquals(java_builder.State.READY, order.state)
        fsm.onEvent(java_builder.Event.FINISH)
        assertEquals(java_builder.State.DONE, order.state)
        assertFails { fsm.onEvent(java_builder.Event.FINISH) }
    }

    @Test
    fun kotlinFluent() {
        val order = kotlin_fluent.Order()
        val fsm = kotlin_fluent.create().getFsmForDomain(order)
        fsm.startAutoTransitions()
        assertEquals(kotlin_fluent.State.READY, order.state)
        fsm.onEvent(kotlin_fluent.Event.FINISH)
        assertEquals(kotlin_fluent.State.DONE, order.state)
        assertFails { fsm.onEvent(kotlin_fluent.Event.FINISH) }
    }

    @Test
    fun kotlinBuilder() {
        val order = kotlin_builder.Order()
        val fsm = kotlin_builder.create().getFsmForDomain(order)
        fsm.startAutoTransitions()
        assertEquals(kotlin_builder.State.READY, order.state)
        fsm.onEvent(kotlin_builder.Event.FINISH)
        assertEquals(kotlin_builder.State.DONE, order.state)
        assertFails { fsm.onEvent(kotlin_builder.Event.FINISH) }
    }
}
