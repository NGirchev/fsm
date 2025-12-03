package ru.girchev.fsm.it

import mu.KLogging
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import ru.girchev.fsm.To
import ru.girchev.fsm.impl.basic.BFsm
import ru.girchev.fsm.impl.basic.BTransition
import ru.girchev.fsm.impl.basic.BTransitionTable
import ru.girchev.fsm.impl.basic.BTransitionTable.*
import ru.girchev.fsm.exception.DuplicateTransitionException
import ru.girchev.fsm.exception.FsmException
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class BFsmIT {
    companion object : KLogging()

    private var autoSendEnabled: Boolean = true
    private var successfullySent: Boolean = false

    private fun provideTransitions(): Stream<Arguments?>? {
        return Stream.of(
            Arguments.of(
                Builder<String>()
                    .autoTransitionEnabled(false)
                    .add(from = "NEW", "READY_FOR_SIGN")
                    .add(
                        from = "READY_FOR_SIGN",
                        To(
                            "SIGNED",
                            condition = { true },
                            action = { logger.info { "SIGNED SUCCESSFUL" } }),
                        To("CANCELLED")
                    )
                    .add(
                        BTransition(
                            from = "SIGNED",
                            To(
                                state = "AUTO_SENT",
                                condition = { autoSendEnabled },
                                action = { successfullySent = true; logger.info { "AUTO SENT ACTION" } }
                            )
                        ))
                    .add(from = "SIGNED", "DONE", "CANCELED")
                    .add(from = "AUTO_SENT", "DONE", "CANCELED")
                    .build()),
            Arguments.of(
                Builder<String>()
                    .autoTransitionEnabled(false)
                    .from("NEW").to("READY_FOR_SIGN").end()
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
    fun shouldThrowFsmExceptionWhenTryToSign(transitions: BTransitionTable<String>) {
        // given
        val fsm = BFsm("NEW", transitions)
        // when
        Assertions.assertThrows(FsmException::class.java) {
            fsm.toState("SIGNED")
        }
        assertEquals("NEW", fsm.getState())
    }

    @ParameterizedTest
    @MethodSource("provideTransitions")
    fun shouldChangeStatusToReadyForSign(transitions: BTransitionTable<String>) {
        // given
        val fsm = BFsm("NEW", transitions)
        // when
        fsm.toState("READY_FOR_SIGN")
        // then
        assertEquals("READY_FOR_SIGN", fsm.getState())
    }

    @ParameterizedTest
    @MethodSource("provideTransitions")
    fun shouldChangeStatusToSigned(transitions: BTransitionTable<String>) {
        // given
        val fsm = BFsm("READY_FOR_SIGN", transitions)
        // when
        fsm.toState("SIGNED")
        // then
        assertEquals("SIGNED", fsm.getState())
    }

    @ParameterizedTest
    @MethodSource("provideTransitions")
    fun shouldChangeStatusToAutoSent(transitions: BTransitionTable<String>) {
        // given
        val fsm = BFsm("SIGNED", transitions)
        // when
        fsm.toState("AUTO_SENT")
        // then
        assertEquals("AUTO_SENT", fsm.getState())
        assertEquals(true, successfullySent)
    }

    @ParameterizedTest
    @MethodSource("provideTransitions")
    fun shouldChangeStatusToDone(transitions: BTransitionTable<String>) {
        // given
        transitions.autoTransitionEnabled = true
        val fsm = transitions.createFsm("READY_FOR_SIGN")
        // when
        fsm.toState("SIGNED")
        // then
        assertEquals("DONE", fsm.getState())
        assertEquals(true, successfullySent)
    }

    @Test
    fun shouldThrowDuplicateStatesWhenAddTheSameTransitions() {
        assertFailsWith(
            exceptionClass = DuplicateTransitionException::class,
            message = "No exception found",
            block = {
                BFsm(
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
    fun shouldAllowAddingMultipleConditions() {
        // Now we can add multiple conditions, and all must be true
        var conditionCallCount = 0
        val transitions = Builder<String>()
            .from("NEW").to("READY_FOR_SIGN").end()
            .from("READY_FOR_SIGN").to("SIGNED")
            .condition { conditionCallCount++; true }
            .condition { conditionCallCount++; true }.end()
            .build()
        val fsm = BFsm("READY_FOR_SIGN", transitions)
        fsm.toState("SIGNED")
        assertEquals("SIGNED", fsm.getState())
        assertEquals(2, conditionCallCount) // Both conditions should be checked
    }

    @Test
    fun shouldAllowAddingMultipleActions() {
        // Now we can add multiple actions, and all will be executed
        var actionCallCount = 0
        val transitions = Builder<String>()
            .from("NEW").to("READY_FOR_SIGN").end()
            .from("READY_FOR_SIGN").to("SIGNED")
            .action { actionCallCount++ }
            .action { actionCallCount++ }.end()
            .build()

        val fsm = BFsm("READY_FOR_SIGN", transitions)
        fsm.toState("SIGNED")
        assertEquals("SIGNED", fsm.getState())
        assertEquals(2, actionCallCount) // Both actions should be executed
    }

    @Test
    fun shouldAllowAddingMultipleConditionsInMultiple() {
        // Now we can add multiple conditions in toMultiple, and all must be true
        var conditionCallCount = 0
        val transitions = Builder<String>()
            .from("NEW").to("READY_FOR_SIGN").end()
            .from("READY_FOR_SIGN").toMultiple()
            .to("SIGNED")
            .condition { conditionCallCount++; true }
            .condition { conditionCallCount++; true }.end()
            .endMultiple()
            .build()

        val fsm = BFsm("READY_FOR_SIGN", transitions)
        fsm.toState("SIGNED")
        assertEquals("SIGNED", fsm.getState())
        assertEquals(2, conditionCallCount) // Both conditions should be checked
    }

    @Test
    fun shouldAllowAddingMultipleActionsInMultiple() {
        // Now we can add multiple actions in toMultiple, and all will be executed
        var actionCallCount = 0
        val transitions = Builder<String>()
            .from("NEW").to("READY_FOR_SIGN").end()
            .from("READY_FOR_SIGN").toMultiple()
            .to("SIGNED")
            .action { actionCallCount++ }
            .action { actionCallCount++ }.end()
            .endMultiple()
            .build()

        val fsm = BFsm("READY_FOR_SIGN", transitions)
        fsm.toState("SIGNED")
        assertEquals("SIGNED", fsm.getState())
        assertEquals(2, actionCallCount) // Both actions should be executed
    }

    @Test
    fun shouldUse2LevelsInDepth() {
        val transitions = Builder<String>()
            .autoTransitionEnabled(true)
            .from("NEW").to("SWITCH").end()
            .from("SWITCH").toMultiple()
            .to("LEVEL1_STATE1").condition { true }.end()
            .to("LEVEL1_STATE2").condition { false }.end()
            .endMultiple()
            .from("LEVEL1_STATE1").toMultiple()
            .to("LEVEL2_STATE1").condition { false }.end()
            .to("LEVEL2_STATE2").condition { true }.end()
            .endMultiple()
            .build()
        val fsm = BFsm("NEW", transitions)
        fsm.toState("SWITCH")
        assertEquals("LEVEL2_STATE2", fsm.getState())
    }
}
