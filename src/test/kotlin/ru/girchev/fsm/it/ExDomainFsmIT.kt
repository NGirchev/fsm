package ru.girchev.fsm.it

import mu.KLogging
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import ru.girchev.fsm.impl.extended.ExDomainFSM
import ru.girchev.fsm.impl.extended.ExTransitionTable
import ru.girchev.fsm.impl.basic.BaTransitionTable.*
import ru.girchev.fsm.impl.AbstractTransitionTable.*
import ru.girchev.fsm.FSMContext
import ru.girchev.fsm.TransitionTable.*
import ru.girchev.fsm.Timeout
import ru.girchev.fsm.exception.FSMEventSourcingTransitionFailedException
import ru.girchev.fsm.exception.FSMException
import ru.girchev.fsm.impl.extended.from
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
                ExDomainFSM(
                ExTransitionTable.Builder<DocumentState, String>()
                    .add(from = NEW, event = "TO_READY", to = READY_FOR_SIGN)
                    .add(from = READY_FOR_SIGN, event = "USER_SIGN", to = SIGNED, timeout = Timeout(1))
                    .add(from = READY_FOR_SIGN, event = "FAILED_EVENT", to = CANCELED)
                    .add(from = SIGNED, event = "FAILED_EVENT", to = CANCELED)
                    .add(
                        from = SIGNED, event = "TO_END",                        // switch case example
                        To(AUTO_SENT, condition = { document.signRequired }),   // first
                        To(DONE, condition = { !document.signRequired }),       // second
                        To(CANCELED)                                            // else
                    )
                    .add(from = AUTO_SENT, event = "TO_END", to = DONE)
                    .build()
            )
            ),
            Arguments.of(
                ExDomainFSM(
                ExTransitionTable.Builder<DocumentState, String>()
                    .from(NEW).to(READY_FOR_SIGN).event("TO_READY").end()
                    .from(READY_FOR_SIGN).toMultiple()
                    .to(SIGNED).event("USER_SIGN").timeout(Timeout(1)).end()
                    .to(CANCELED).event("FAILED_EVENT").end().endMultiple()
                    .from(SIGNED).event("FAILED_EVENT").to(CANCELED).end()
                    .from(SIGNED).event("TO_END").toMultiple()                  // switch case example
                    .to(AUTO_SENT).condition { document.signRequired }.end()    // first
                    .to(DONE).condition { !document.signRequired }.end()        // second
                    .to(CANCELED).end().endMultiple()                           // else
                    .from(AUTO_SENT).event("TO_END").to(DONE).end()
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
    fun shouldThrowFSMExceptionWhenFailedEvent(fsm: ExDomainFSM<Document, DocumentState, String>) {
        // given
        // when
        Assertions.assertThrows(FSMException::class.java) {
            fsm.handle(document, "FAILED_EVENT")
        }
        assertEquals(NEW, document.state)
    }

    @ParameterizedTest
    @MethodSource("provideTransitions")
    fun shouldChangeStatusToReadyForSignWhenToReadyEvent(fsm: ExDomainFSM<Document, DocumentState, String>) {
        // given
        document.state = NEW
        // when
        fsm.handle(document, "TO_READY")
        // then
        assertEquals(READY_FOR_SIGN, document.state)
    }

    @ParameterizedTest
    @MethodSource("provideTransitions")
    fun shouldChangeStatusToAutoSentWhenUserSignEvent(fsm: ExDomainFSM<Document, DocumentState, String>) {
        // given
        document.state = READY_FOR_SIGN
        // when
        fsm.handle(document, "USER_SIGN")
        // then
        assertEquals(SIGNED, document.state)
    }

    @ParameterizedTest
    @MethodSource("provideTransitions")
    fun shouldChangeStatusToDoneWhenToEndEvent(fsm: ExDomainFSM<Document, DocumentState, String>) {
        // given
        document.state = SIGNED
        // when
        fsm.handle(document, "TO_END")
        // then
        assertEquals(DONE, document.state)
    }

    @ParameterizedTest
    @MethodSource("provideTransitions")
    fun shouldChangeStatusToAutoSentWhenSignRequiredAndToEndEvent(fsm: ExDomainFSM<Document, DocumentState, String>) {
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
    fun shouldChangeStatusToDoneWhenSignRequiredAndToEndEvent(fsm: ExDomainFSM<Document, DocumentState, String>) {
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
        val fsm = ExDomainFSM(
            ExTransitionTable.Builder<DocumentState, String>()
                .add(from = SIGNED, event = "FAILED_EVENT", to = CANCELED)
                .add(
                    from = SIGNED, event = "TO_END",                        // switch case example
                    To(AUTO_SENT, condition = { false }),                   // first
                    To(DONE, condition = { false }),                        // second
                    To(CANCELED)                                            // else
                )
                .add(from = AUTO_SENT, event = "TO_END", to = DONE)
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
        val fsm = ExDomainFSM(
            ExTransitionTable.Builder<DocumentState, String>()
                .add(from = SIGNED, event = "FAILED_EVENT", to = CANCELED)
                .add(
                    from = SIGNED, event = "TO_END",                        // switch case example
                    To(AUTO_SENT, condition = { false }),                   // first
                    To(DONE, condition = { false }),                        // second
                    To(CANCELED, condition = { false })                     // third
                )
                .add(from = AUTO_SENT, event = "TO_END", to = DONE)
                .build()
        )
        document.state = SIGNED
        // when
        Assertions.assertThrows(FSMEventSourcingTransitionFailedException::class.java) {
            fsm.handle(document, "TO_END")
        }
        // then
        assertEquals(SIGNED, document.state)
    }

    @Test
    fun shouldChangeStatusOnlyOnceEvenDocEachTimeIsNew() {
        val fsm = ExDomainFSM(
            ExTransitionTable.Builder<DocumentState, String>()
                .add(
                    from = NEW, event = "TO_END",
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
        val fsm = ExDomainFSM(
            ExTransitionTable.Builder<DocumentState, String>()
                .add(from = NEW, event = "TO_END", to = READY_FOR_SIGN)
                .add(
                    from = READY_FOR_SIGN, event = null,
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
        val fsm = ExDomainFSM(
            ExTransitionTable.Builder<DocumentState, String>()
                .add(
                    from = NEW, event = "TO_END", to = READY_FOR_SIGN,
                    action = { prt(it) },
                    timeout = Timeout(1)
                )
                .add(
                    from = READY_FOR_SIGN, event = null,
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

    private fun prt(fsmContext: FSMContext<DocumentState>) {
        logger.info { "Transition from=[" + fsmContext.state + "] at=" + System.currentTimeMillis() }
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
}
