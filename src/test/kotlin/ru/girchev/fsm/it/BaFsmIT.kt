package ru.girchev.fsm.it

import mu.KLogging
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import ru.girchev.fsm.impl.basic.BaFSM
import ru.girchev.fsm.impl.basic.BaTransition
import ru.girchev.fsm.impl.basic.BaTransitionTable
import ru.girchev.fsm.impl.basic.BaTransitionTable.*
import ru.girchev.fsm.impl.basic.from
import ru.girchev.fsm.TransitionTable.*
import ru.girchev.fsm.exception.DuplicateTransitionException
import ru.girchev.fsm.exception.FSMException
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class BaFsmIT {
    companion object : KLogging()

    private var autoSendEnabled: Boolean = true
    private var successfullySent: Boolean = false

    private fun provideTransitions(): Stream<Arguments?>? {
        return Stream.of(
            Arguments.of(Builder<String>()
                .add(from = "NEW", "READY_FOR_SIGN")
                .add(
                    from = "READY_FOR_SIGN",
                    To(
                        "SIGNED",
                        condition = { true },
                        action = { logger.info { "SIGNED SUCCESSFUL" } }),
                    To("CANCELLED")
                )
                .add(BaTransition(
                    from = "SIGNED",
                    to = "AUTO_SENT",
                    condition = { autoSendEnabled },
                    action = { successfullySent = true; logger.info { "AUTO SENT ACTION" } }
                ))
                .add(from = "SIGNED", "DONE", "CANCELED")
                .add(from = "AUTO_SENT", "DONE", "CANCELED")
                .build()),
            Arguments.of(
                Builder<String>().from("NEW").to("READY_FOR_SIGN").end()
                    .from("READY_FOR_SIGN").toMultiple()
                    .to("SIGNED").condition { true }.action { logger.info { "SIGNED SUCCESSFUL" } }.end()
                    .to("CANCELED").end().endMultiple()
                    .from("SIGNED").to("AUTO_SENT")
                    .condition { autoSendEnabled }
                    .action { successfullySent = true; logger.info { "AUTO SENT ACTION" } }.end()
                    .from("SIGNED").toMultiple().to("DONE").end().to("CANCELED").end().endMultiple()
                    .from("AUTO_SENT").toMultiple().to("DONE").end().to("CANCELED").end().endMultiple()
                    .build()
            )
        )
    }

    @ParameterizedTest
    @MethodSource("provideTransitions")
    fun shouldThrowFSMExceptionWhenTryToSign(transitions: BaTransitionTable<String>) {
        // given
        val fsm = BaFSM("NEW", transitions)
        // when
        Assertions.assertThrows(FSMException::class.java) {
            fsm.toState("SIGNED")
        }
        assertEquals("NEW", fsm.getState())
    }

    @ParameterizedTest
    @MethodSource("provideTransitions")
    fun shouldChangeStatusToReadyForSign(transitions: BaTransitionTable<String>) {
        // given
        val fsm = BaFSM("NEW", transitions)
        // when
        fsm.toState("READY_FOR_SIGN")
        // then
        assertEquals("READY_FOR_SIGN", fsm.getState())
    }

    @ParameterizedTest
    @MethodSource("provideTransitions")
    fun shouldChangeStatusToSigned(transitions: BaTransitionTable<String>) {
        // given
        val fsm = BaFSM("READY_FOR_SIGN", transitions)
        // when
        fsm.toState("SIGNED")
        // then
        assertEquals("SIGNED", fsm.getState())
    }

    @ParameterizedTest
    @MethodSource("provideTransitions")
    fun shouldChangeStatusToAutoSent(transitions: BaTransitionTable<String>) {
        // given
        val fsm = BaFSM("SIGNED", transitions)
        // when
        fsm.toState("AUTO_SENT")
        // then
        assertEquals("AUTO_SENT", fsm.getState())
        assertEquals(true, successfullySent)
    }

    @Test
    fun shouldThrowDuplicateStatesWhenAddTheSameTransitions() {
        assertFailsWith(
            exceptionClass = DuplicateTransitionException::class,
            message = "No exception found",
            block = {
                BaFSM(
                    "NEW",
                    Builder<String>()
                        .add(from = "NEW", To("READY_FOR_SIGN"))
                        .add(from = "NEW", To("READY_FOR_SIGN"))
                        .build()
                )
            }
        )
    }

    @Test
    fun shouldThrowFSMExceptionWhenAddConditionTwice() {
        assertFailsWith(
            exceptionClass = FSMException::class,
            message = "No exception found",
            block = {
                Builder<String>()
                    .from("NEW").to("READY_FOR_SIGN").end()
                    .from("READY_FOR_SIGN").to("SIGNED").condition { true }.condition { true }.end()
                    .build()
            }
        )
    }

    @Test
    fun shouldThrowFSMExceptionWhenAddActionTwice() {
        assertFailsWith(
            exceptionClass = FSMException::class,
            message = "No exception found",
            block = {
                Builder<String>()
                    .from("NEW").to("READY_FOR_SIGN").end()
                    .from("READY_FOR_SIGN").to("SIGNED")
                    .action { logger.info { "Action" } }
                    .action { logger.info { "Action" } }.end()
                    .build()
            }
        )
    }

    @Test
    fun shouldThrowFSMExceptionWhenAddConditionTwiceInMultiple() {
        assertFailsWith(
            exceptionClass = FSMException::class,
            message = "No exception found",
            block = {
                Builder<String>()
                    .from("NEW").to("READY_FOR_SIGN").end()
                    .from("READY_FOR_SIGN").toMultiple()
                    .to("SIGNED").action { logger.info { "Action" } }.action { logger.info { "Action" } }.end()
                    .endMultiple()
                    .build()
            }
        )
    }

    @Test
    fun shouldThrowFSMExceptionWhenAddActionTwiceInMultiple() {
        assertFailsWith(
            exceptionClass = FSMException::class,
            message = "No exception found",
            block = {
                Builder<String>()
                    .from("NEW").to("READY_FOR_SIGN").end()
                    .from("READY_FOR_SIGN").toMultiple()
                    .to("SIGNED").action { logger.info { "Action" } }.action { logger.info { "Action" } }.end()
                    .endMultiple()
                    .build()
            }
        )
    }
}
