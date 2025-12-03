package ru.girchev.fsm.it

import mu.KLogging
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import ru.girchev.fsm.impl.extended.ExDomainFsm
import ru.girchev.fsm.impl.extended.ExTransitionTable
import ru.girchev.fsm.StateContext
import ru.girchev.fsm.Timeout
import ru.girchev.fsm.To
import ru.girchev.fsm.exception.FsmEventSourcingTransitionFailedException
import ru.girchev.fsm.exception.FsmException
import ru.girchev.fsm.it.document.Document
import ru.girchev.fsm.it.document.DocumentState
import ru.girchev.fsm.it.document.DocumentState.*
import java.time.Duration
import java.time.Instant
import java.util.concurrent.*
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ExDomainFsmIT {

    companion object : KLogging()

    private lateinit var document: Document

    private fun provideTransitions(): Stream<Arguments?>? {
        return Stream.of(
            Arguments.of(
                ExDomainFsm(
                    ExTransitionTable.Builder<DocumentState, String>()
                        .add(from = NEW, onEvent = "TO_READY", to = READY_FOR_SIGN)
                        .add(from = READY_FOR_SIGN, onEvent = "USER_SIGN", to = SIGNED, timeout = Timeout(1))
                        .add(from = READY_FOR_SIGN, onEvent = "FAILED_EVENT", to = CANCELED)
                        .add(from = SIGNED, onEvent = "FAILED_EVENT", to = CANCELED)
                        .add(
                            from = SIGNED, onEvent = "TO_END",                        // switch case example
                            To(AUTO_SENT, condition = { document.signRequired }),   // first
                            To(DONE, condition = { !document.signRequired }),       // second
                            To(CANCELED)                                            // else
                        )
                        .add(from = AUTO_SENT, onEvent = "TO_END", to = DONE)
                        .build()
                )
            ),
            Arguments.of(
                ExDomainFsm(
                    ExTransitionTable.Builder<DocumentState, String>()
                        .from(NEW).to(READY_FOR_SIGN).onEvent("TO_READY").end()
                        .from(READY_FOR_SIGN).toMultiple()
                        .to(SIGNED).onEvent("USER_SIGN").timeout(Timeout(1)).end()
                        .to(CANCELED).onEvent("FAILED_EVENT").end().endMultiple()
                        .from(SIGNED).onEvent("FAILED_EVENT").to(CANCELED).end()
                        .from(SIGNED).onEvent("TO_END").toMultiple()                  // switch case example
                        .to(AUTO_SENT).condition { document.signRequired }.end()    // first
                        .to(DONE).condition { !document.signRequired }.end()        // second
                        .to(CANCELED).end().endMultiple()                           // else
                        .from(AUTO_SENT).onEvent("TO_END").to(DONE).end()
                        .build()
                )
            )
        )
    }

    @BeforeEach
    internal fun setUp() {
        document = Document()
    }

    @ParameterizedTest
    @MethodSource("provideTransitions")
    fun shouldThrowFsmExceptionWhenFailedEvent(fsm: ExDomainFsm<Document, DocumentState, String>) {
        // given
        // when
        Assertions.assertThrows(FsmException::class.java) {
            fsm.handle(document, "FAILED_EVENT")
        }
        assertEquals(NEW, document.state)
    }

    @ParameterizedTest
    @MethodSource("provideTransitions")
    fun shouldChangeStatusToReadyForSignWhenToReadyEvent(fsm: ExDomainFsm<Document, DocumentState, String>) {
        // given
        document.state = NEW
        // when
        fsm.handle(document, "TO_READY")
        // then
        assertEquals(READY_FOR_SIGN, document.state)
    }

    @ParameterizedTest
    @MethodSource("provideTransitions")
    fun shouldChangeStatusToAutoSentWhenUserSignEvent(fsm: ExDomainFsm<Document, DocumentState, String>) {
        // given
        document.state = READY_FOR_SIGN
        // when
        fsm.handle(document, "USER_SIGN")
        // then
        assertEquals(SIGNED, document.state)
    }

    @ParameterizedTest
    @MethodSource("provideTransitions")
    fun shouldChangeStatusToDoneWhenToEndEvent(fsm: ExDomainFsm<Document, DocumentState, String>) {
        // given
        document.state = SIGNED
        // when
        fsm.handle(document, "TO_END")
        // then
        assertEquals(DONE, document.state)
    }

    @ParameterizedTest
    @MethodSource("provideTransitions")
    fun shouldChangeStatusToAutoSentWhenSignRequiredAndToEndEvent(fsm: ExDomainFsm<Document, DocumentState, String>) {
        // given
        document = Document(signRequired = true)
        document.state = SIGNED
        // when
        fsm.handle(document, "TO_END")

        // then
        assertEquals(AUTO_SENT, document.state)
    }

    @ParameterizedTest
    @MethodSource("provideTransitions")
    fun shouldChangeStatusToDoneWhenSignRequiredAndToEndEvent(fsm: ExDomainFsm<Document, DocumentState, String>) {
        // given
        document = Document(signRequired = true)
        document.state = AUTO_SENT
        // when
        fsm.handle(document, "TO_END")
        // then
        assertEquals(DONE, document.state)
    }

    @Test
    fun shouldChangeStatusToCancelledWhenBothConditionIsFalse() {
        // given
        val fsm = ExDomainFsm(
            ExTransitionTable.Builder<DocumentState, String>()
                .add(from = SIGNED, onEvent = "FAILED_EVENT", to = CANCELED)
                .add(
                    from = SIGNED, onEvent = "TO_END",                        // switch case example
                    To(AUTO_SENT, condition = { false }),                   // first
                    To(DONE, condition = { false }),                        // second
                    To(CANCELED)                                            // else
                )
                .add(from = AUTO_SENT, onEvent = "TO_END", to = DONE)
                .build()
        )
        document.state = SIGNED
        // when
        fsm.handle(document, "TO_END")
        // then
        assertEquals(CANCELED, document.state)
    }

    @Test
    fun shouldNotChangeStatusToCancelledWhenAllConditionIsFalse() {
        // given
        val fsm = ExDomainFsm(
            ExTransitionTable.Builder<DocumentState, String>()
                .add(from = SIGNED, onEvent = "FAILED_EVENT", to = CANCELED)
                .add(
                    from = SIGNED, onEvent = "TO_END",                        // switch case example
                    To(AUTO_SENT, condition = { false }),                   // first
                    To(DONE, condition = { false }),                        // second
                    To(CANCELED, condition = { false })                     // third
                )
                .add(from = AUTO_SENT, onEvent = "TO_END", to = DONE)
                .build()
        )
        document.state = SIGNED
        // when
        Assertions.assertThrows(FsmEventSourcingTransitionFailedException::class.java) {
            fsm.handle(document, "TO_END")
        }
        // then
        assertEquals(SIGNED, document.state)
    }

    @Test
    fun shouldChangeStatusOnlyOnceEvenDocEachTimeIsNew() {
        val fsm = ExDomainFsm(
            ExTransitionTable.Builder<DocumentState, String>()
                .add(
                    from = NEW, onEvent = "TO_END",
                    To(READY_FOR_SIGN, condition = { true }),
                    To(SIGNED, condition = { true }),
                    To(CANCELED)
                )
                .build()
        )
        val futureList = ArrayList<CompletableFuture<Boolean>>()
        for (x in 1..10) {
            futureList.add(CompletableFuture.supplyAsync {
                val document = Document()
                fsm.handle(document, "TO_END")
                return@supplyAsync READY_FOR_SIGN == document.state
            })
        }
        assertTrue { allTrue(futureList) }
    }

    @Test
    fun shouldChangeStatusToFirstInSwitchWhenEventIsNull() {
        val fsm = ExDomainFsm(
            ExTransitionTable.Builder<DocumentState, String>()
                .autoTransitionEnabled(true)
                .add(from = NEW, onEvent = "TO_END", to = READY_FOR_SIGN)
                .add(
                    from = READY_FOR_SIGN, onEvent = null,
                    To(SIGNED, condition = { true }),
                    To(AUTO_SENT, condition = { true }),
                    To(DONE, condition = { true }),
                    To(CANCELED)
                )
                .build()
        )
        val futureList = ArrayList<CompletableFuture<Boolean>>()
        for (x in 1..10) {
            futureList.add(CompletableFuture.supplyAsync {
                val document = Document()
                fsm.handle(document, "TO_END")
                return@supplyAsync SIGNED == document.state
            })
        }
        assertTrue { allTrue(futureList) }
    }

    @Test
    fun shouldChangeStatusToDoneByTimeout() {
        document = Document()
        val fsm = ExDomainFsm(
            ExTransitionTable.Builder<DocumentState, String>()
                .autoTransitionEnabled(true)
                .add(
                    from = NEW, onEvent = "TO_END", to = READY_FOR_SIGN,
                    action = { prt(it) },
                    timeout = Timeout(1)
                )
                .add(
                    from = READY_FOR_SIGN, onEvent = null,
                    To(
                        CANCELED, condition = { false },
                        action = { prt(it) },
                        timeout = Timeout(1)
                    ),
                    To(
                        SIGNED, condition = { true },
                        action = { prt(it) },
                        timeout = Timeout(1)
                    ),
                )
                .add(
                    from = SIGNED, to = AUTO_SENT,
                    action = { prt(it) },
                    timeout = Timeout(1)
                )
                .add(
                    from = AUTO_SENT, to = DONE, action = { prt(it) },
                    timeout = Timeout(1)
                )
                .build()
        )
        val start = System.currentTimeMillis()
        fsm.handle(document, "TO_END")
        val end = System.currentTimeMillis()

        assertEquals(DONE, document.state)
        assertTrue(
            Duration.between(
                Instant.ofEpochMilli(start),
                Instant.ofEpochMilli(end)
            ).seconds >= 4
        )
    }

    private fun prt(stateContext: StateContext<DocumentState>) {
        logger.info { "Transition from=[" + stateContext.state + "] at=" + System.currentTimeMillis() }
    }

    private fun allTrue(futures: List<CompletableFuture<Boolean>>): Boolean {
        val cfs = futures.toTypedArray<CompletableFuture<*>>()
        return CompletableFuture.allOf(*cfs)
            .thenApply {
                futures.stream()
                    .map { obj: CompletableFuture<Boolean> -> obj.join() }
                    .collect(Collectors.toList())
            }.join()
            .stream().allMatch { result -> result.equals(true) }
    }

    @Test
    fun shouldExecuteMultipleConditionsAndActions() {
        // Test that multiple conditions and actions can be added and all execute
        var conditionCallCount = 0
        var actionCallCount = 0
        var postActionCallCount = 0

        val fsm = ExDomainFsm(
            ExTransitionTable.Builder<DocumentState, String>()
                .from(NEW).onEvent("PROCESS").to(READY_FOR_SIGN)
                .condition { conditionCallCount++; true }
                .condition { conditionCallCount++; true }
                .action { actionCallCount++ }
                .action { actionCallCount++ }
                .postAction { postActionCallCount++ }
                .postAction { postActionCallCount++ }
                .end()
                .build()
        )

        document = Document()
        document.state = NEW

        // when
        fsm.handle(document, "PROCESS")

        // then
        assertEquals(READY_FOR_SIGN, document.state)
        assertEquals(2, conditionCallCount, "Both conditions should be checked")
        assertEquals(2, actionCallCount, "Both actions should be executed")
        assertEquals(2, postActionCallCount, "Both postActions should be executed")
    }

    @Test
    fun shouldFailWhenOneConditionIsFalse() {
        // Test that if one of multiple conditions is false, transition fails
        var condition1Called = false
        var condition2Called = false

        val fsm = ExDomainFsm(
            ExTransitionTable.Builder<DocumentState, String>()
                .from(NEW).onEvent("PROCESS").to(READY_FOR_SIGN)
                .condition { condition1Called = true; true }
                .condition { condition2Called = true; false }  // This one returns false
                .end()
                .build()
        )

        document = Document()
        document.state = NEW

        // when - should throw exception because one condition is false
        Assertions.assertThrows(FsmException::class.java) {
            fsm.handle(document, "PROCESS")
        }

        // then
        assertEquals(NEW, document.state, "State should not change")
        assertTrue(condition1Called, "First condition should be checked")
        assertTrue(condition2Called, "Second condition should be checked")
    }
}
