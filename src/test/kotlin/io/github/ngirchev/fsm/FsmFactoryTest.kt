package io.github.ngirchev.fsm

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class FsmFactoryTest {

    @Test
    fun statesShouldReturnBTransitionTableBuilder() {
        val builder = FsmFactory.states<String>()
        assertNotNull(builder)
    }

    @Test
    fun statesWithEventsShouldReturnExTransitionTableBuilder() {
        val builder = FsmFactory.statesWithEvents<String, String>()
        assertNotNull(builder)
    }
}
