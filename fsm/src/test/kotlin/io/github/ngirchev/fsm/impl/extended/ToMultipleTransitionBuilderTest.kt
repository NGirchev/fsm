package io.github.ngirchev.fsm.impl.extended

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import io.github.ngirchev.fsm.AutoTransitionScheduler
import io.github.ngirchev.fsm.exception.FsmException
import io.github.ngirchev.fsm.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ToMultipleTransitionBuilderTest {

    @Test
    fun timeoutWhenCalledTwiceThenThrowException() {
        val builder = ExTransitionTable.Builder<String, String>()
        val toMultipleBuilder = builder.from("from").toMultiple()
        val toMultipleTransitionBuilder = toMultipleBuilder.to("to")

        toMultipleTransitionBuilder.timeout(Timeout(1))

        val exception = assertThrows(FsmException::class.java) {
            toMultipleTransitionBuilder.timeout(Timeout(2))
        }

        assertEquals("Already has timeout", exception.message)
    }

    @Test
    fun endShouldReturnToMultipleBuilder() {
        val builder = ExTransitionTable.Builder<String, String>()
        val toMultipleBuilder = builder.from("from").toMultiple()
        val toMultipleTransitionBuilder = toMultipleBuilder.to("to")

        val result = toMultipleTransitionBuilder.end()

        assertEquals(toMultipleBuilder, result)
    }

    @Test
    fun autoShouldMarkTransitionAsAutoAndAllowDeferWith() {
        val scheduler = AutoTransitionScheduler<String> { _, _, runTransition -> runTransition() }
        val builder = ExTransitionTable.Builder<String, String>()
        val toMultipleBuilder = builder.from("from").toMultiple()

        toMultipleBuilder.to("to")
            .auto()
            .deferWith(scheduler)
            .end()
            .endMultiple()

        val transition = builder.build().transitions["from"]?.single() ?: error("Expected transition")
        assertTrue(transition.to.autoTransitionEnabled)
        assertSame(scheduler, transition.to.autoTransitionScheduler)
    }

    @Test
    fun autoShouldRejectEventTransition() {
        val builder = ExTransitionTable.Builder<String, String>()
        val toMultipleBuilder = builder.from("from").toMultiple()
        val toMultipleTransitionBuilder = toMultipleBuilder.to("to", "event")

        val exception = assertThrows(FsmException::class.java) {
            toMultipleTransitionBuilder.auto()
        }

        assertEquals("Only eventless auto transitions can be configured as auto", exception.message)
    }

    @Test
    fun endShouldAddTransitionToMultipleBuilder() {
        val builder = ExTransitionTable.Builder<String, String>()
        val toMultipleBuilder = builder.from("from").toMultiple()
        val toMultipleTransitionBuilder = toMultipleBuilder
            .onEvent("event")
            .to("to")
            .onCondition { true }
            .action { }
            .postAction { }
            .timeout(Timeout(1))

        toMultipleTransitionBuilder.end()
        toMultipleBuilder.endMultiple()

        val table = builder.build()
        val transition = table.transitions["from"]?.single() ?: error("Expected transition")
        assertEquals("event", transition.event)
    }
}
