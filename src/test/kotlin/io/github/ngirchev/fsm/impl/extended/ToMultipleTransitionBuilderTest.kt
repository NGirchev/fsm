package io.github.ngirchev.fsm.impl.extended

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import io.github.ngirchev.fsm.exception.FsmException
import io.github.ngirchev.fsm.Timeout
import kotlin.test.assertEquals

class ToMultipleTransitionBuilderTest {

    @Test
    fun onEventWhenCalledTwiceThenThrowException() {
        val builder = ExTransitionTable.Builder<String, String>()
        val toMultipleBuilder = builder.from("from").toMultiple()
        val toMultipleTransitionBuilder = toMultipleBuilder.to("to")

        toMultipleTransitionBuilder.onEvent("event1")

        val exception = assertThrows(FsmException::class.java) {
            toMultipleTransitionBuilder.onEvent("event2")
        }

        assertEquals("Already has event", exception.message)
    }

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
    fun endShouldAddTransitionToMultipleBuilder() {
        val builder = ExTransitionTable.Builder<String, String>()
        val toMultipleBuilder = builder.from("from").toMultiple()
        val toMultipleTransitionBuilder = toMultipleBuilder.to("to")
            .onEvent("event")
            .condition { true }
            .action { }
            .postAction { }
            .timeout(Timeout(1))

        toMultipleTransitionBuilder.end()
        toMultipleBuilder.endMultiple()

        val table = builder.build()
        assertEquals(1, table.transitions.size)
    }
}
