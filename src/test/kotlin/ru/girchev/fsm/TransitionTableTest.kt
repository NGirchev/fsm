package ru.girchev.fsm

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import ru.girchev.fsm.exception.DuplicateTransitionException
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.assertEquals

class TransitionTableTest {

    @Test
    @DisplayName("Should add the transition when the transition is not added")
    fun addWhenTransitionIsNotAddedThenAddTheTransition() {
        @Suppress("UNCHECKED_CAST")
        val transition: Transition<String, String> = mock(Transition::class.java as Class<Transition<String, String>>)
        val builder = TransitionTable.Builder<String, String>()
        builder.add(transition)
        assertEquals(expected = 1, actual = builder.transitions.size, message = "")
    }

    @Test
    @DisplayName("Should throw an exception when the transition is already added")
    fun addWhenTransitionIsAlreadyAddedThenThrowException() {
        val builder = TransitionTable.Builder<String, String>()
        val transition = Transition("from", "event", "to")
        builder.add(transition)

        val exception = assertThrows(DuplicateTransitionException::class.java) {
            builder.add(transition)
        }

        assertEquals("Can't add duplicate transition [from]->[to]", exception.message)
    }
}