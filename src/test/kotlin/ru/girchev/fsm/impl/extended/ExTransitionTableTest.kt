package ru.girchev.fsm.impl.extended

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import ru.girchev.fsm.exception.DuplicateTransitionException
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.assertEquals

class ExTransitionTableTest {

    @Test
    @DisplayName("Should add the transition when the transition is not added")
    fun addWhenTransitionIsNotAddedThenAddTheTransition() {
        val transition: ExTransition<String, String> = mock()
        val builder = ExTransitionTable.Builder<String, String>()
        builder.add(transition)
        assertEquals(expected = 1, actual = builder.transitions.size, message = "")
    }

    @Test
    @DisplayName("Should throw an exception when the transition is already added")
    fun addWhenTransitionIsAlreadyAddedThenThrowException() {
        val builder = ExTransitionTable.Builder<String, String>()
        val transition = ExTransition("from", "to", "event")
        builder.add(transition)

        val exception = assertThrows(DuplicateTransitionException::class.java) {
            builder.add(transition)
        }

        assertEquals("Can't add duplicate transition [from]->[to]", exception.message)
    }
}