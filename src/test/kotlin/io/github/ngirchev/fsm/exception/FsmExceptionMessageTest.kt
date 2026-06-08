package io.github.ngirchev.fsm.exception

import kotlin.test.Test
import kotlin.test.assertEquals

class FsmExceptionMessageTest {

    @Test
    fun transitionFailureShouldFormatDomainNameAndText() {
        val exception = FsmTransitionFailedException(
            source = "NEW",
            target = "DONE",
            domainName = "Document",
            text = "guard failed",
        )

        assertEquals("Illegal document state transition NEW->DONE, guard failed", exception.message)
    }

    @Test
    fun eventTransitionFailureShouldFormatDomainNameAndText() {
        val exception = FsmEventSourcingTransitionFailedException(
            source = "NEW",
            event = "APPROVE",
            domainName = "Document",
            text = "guard failed",
        )

        assertEquals(
            "Illegal document state transition for state=[NEW] by event=[APPROVE], guard failed",
            exception.message,
        )
    }
}
