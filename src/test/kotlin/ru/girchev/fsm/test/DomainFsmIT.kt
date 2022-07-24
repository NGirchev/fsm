package ru.girchev.fsm.test

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.girchev.fsm.test.document.DocumentState.*
import ru.girchev.fsm.TransitionTable
import ru.girchev.fsm.FSMContext
import ru.girchev.fsm.core.SimpleTransitionTable.*
import ru.girchev.fsm.DomainFSM
import ru.girchev.fsm.core.Timeout
import ru.girchev.fsm.exception.FSMException
import ru.girchev.fsm.test.document.Document
import ru.girchev.fsm.test.document.DocumentState
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class DomainFsmIT {

    private lateinit var document: Document
    private lateinit var fsm: DomainFSM<FSMContext<DocumentState>, DocumentState, String>

    @BeforeEach
    internal fun setUp() {
        document = Document()
        fsm = DomainFSM(
            TransitionTable.Builder<DocumentState, String>()
                .add(from = NEW, event = "TO_READY", to = READY_FOR_SIGN)
                .add(from = READY_FOR_SIGN, event = "USER_SIGN", to = SIGNED)
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
        println("Initial state ${document.state}")
    }

    @Test
    fun fullPathWithSignNotRequired_Success() {
        try {
            fsm.handle(document, "FAILED_EVENT")
        } catch (ex: Exception) {
            println("$ex")
            assertNotNull(ex)
            assertTrue(ex is FSMException)
        }

        assertEquals(NEW, document.state)
        println("State still the same ${document.state}")

        fsm.handle(document, "TO_READY")
        assertEquals(READY_FOR_SIGN, document.state)

        fsm.handle(document, "USER_SIGN")
        assertEquals(SIGNED, document.state)

        fsm.handle(document, "TO_END")
        assertEquals(DONE, document.state)
        println("Terminal state ${document.state}")
    }

    @Test
    fun fullPathWithSignRequired_Success() {
        document = Document(signRequired = true)
        try {
            fsm.handle(document, "FAILED_EVENT")
        } catch (ex: Exception) {
            println("$ex")
            assertNotNull(ex)
            assertTrue(ex is FSMException)
        }

        assertEquals(NEW, document.state)
        println("State still the same ${document.state}")

        fsm.handle(document, "TO_READY")
        assertEquals(READY_FOR_SIGN, document.state)

        fsm.handle(document, "USER_SIGN")
        assertEquals(SIGNED, document.state)

        fsm.handle(document, "TO_END")
        assertEquals(AUTO_SENT, document.state)

        fsm.handle(document, "TO_END")
        assertEquals(DONE, document.state)
        println("Terminal state ${document.state}")
    }

    @Test
    fun fullPathWithLastStateSwitch_Success() {
        fsm = DomainFSM(
            TransitionTable.Builder<DocumentState, String>()
                .add(from = NEW, event = "TO_READY", to = READY_FOR_SIGN)
                .add(from = READY_FOR_SIGN, event = "USER_SIGN", to = SIGNED)
                .add(from = READY_FOR_SIGN, event = "FAILED_EVENT", to = CANCELED)
                .add(from = SIGNED, event = "FAILED_EVENT", to = CANCELED)
                .add(
                    from = SIGNED, event = "TO_END",
                    To(AUTO_SENT, condition = { false }),
                    To(DONE, condition = { false }),
                    To(CANCELED)
                )
                .add(from = AUTO_SENT, event = "TO_END", to = DONE)
                .build()
        )
        println("Initial state ${document.state}")
        try {
            fsm.handle(document, "FAILED_EVENT")
        } catch (ex: Exception) {
            println("$ex")
            assertNotNull(ex)
            assertTrue(ex is FSMException)
        }

        assertEquals(NEW, document.state)
        println("State still the same ${document.state}")

        fsm.handle(document, "TO_READY")
        assertEquals(READY_FOR_SIGN, document.state)

        fsm.handle(document, "USER_SIGN")
        assertEquals(SIGNED, document.state)

        fsm.handle(document, "TO_END")
        assertEquals(CANCELED, document.state)

        println("Terminal state ${document.state}")
    }

    @Test
    fun allConditionsAreTrueInSwitch_Success() {
        for (x in 1..10000) {
            document = Document()
            fsm = DomainFSM(
                TransitionTable.Builder<DocumentState, String>()
                    .add(
                        from = NEW, event = "TO_END",
                        To(READY_FOR_SIGN, condition = { true }),
                        To(SIGNED, condition = { true }),
                        To(AUTO_SENT, condition = { true }),
                        To(DONE, condition = { true }),
                        To(CANCELED)
                    )
                    .build()
            )
            fsm.handle(document, "TO_END")

            assertEquals(READY_FOR_SIGN, document.state)
        }
    }

    @Test
    fun allConditionsAreTrueInSwitchWithAutoTransition_Success() {
        for (x in 1..10000) {
            document = Document()
            fsm = DomainFSM(
                TransitionTable.Builder<DocumentState, String>()
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
            fsm.handle(document, "TO_END")

            assertEquals(SIGNED, document.state, "Iteration $x")
        }
    }

    @Test
    fun useTimeout_Success() {
        document = Document()
        fsm = DomainFSM(
            TransitionTable.Builder<DocumentState, String>()
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
        println("Transition from=[" + fsmContext.state + "] at=" + System.currentTimeMillis())
    }
}
