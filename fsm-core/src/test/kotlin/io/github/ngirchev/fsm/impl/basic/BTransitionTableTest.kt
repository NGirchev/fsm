package io.github.ngirchev.fsm.impl.basic

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import io.github.ngirchev.fsm.AutoTransitionScheduler
import io.github.ngirchev.fsm.exception.DuplicateTransitionException
import io.github.ngirchev.fsm.To
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.impl.TransitionTableDiagnostics
import io.github.ngirchev.fsm.it.document.Document
import io.github.ngirchev.fsm.it.document.DocumentState
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BTransitionTableTest {

    private class SimpleStateContext(override var state: String, override var currentTransition: io.github.ngirchev.fsm.Transition<String>? = null) : StateContext<String>

    @Test
    fun addWithVarargStateShouldAddMultipleTransitions() {
        val builder = BTransitionTable.Builder<String>()
        builder.add("from", "to1", "to2", "to3")

        val table = builder.build()
        assertEquals(1, table.transitions.size)
        assertEquals(3, table.transitions["from"]?.size)
    }

    @Test
    fun buildShouldSnapshotTransitionsFromBuilder() {
        val builder = BTransitionTable.Builder<String>()
            .add("from", "to")
        val table = builder.build()

        builder.add("from", "later")

        assertEquals(1, table.transitions["from"]?.size)
        assertEquals("to", table.transitions["from"]?.single()?.to?.state)
    }

    @Test
    fun buildWithFactoryShouldCreateCustomTransitionTable() {
        val table = BTransitionTable.Builder<DocumentState>()
            .add(DocumentState.NEW, DocumentState.READY_FOR_SIGN)
            .build(::CustomBTransitionTable)

        assertEquals(CustomBTransitionTable::class, table::class)
    }

    @Test
    fun addWithVarargTransitionShouldAddTransitions() {
        val builder = BTransitionTable.Builder<String>()
        val transition1 = BTransition("from", "to1")
        val transition2 = BTransition("from", "to2")
        builder.add(transition1, transition2)

        val table = builder.build()
        assertEquals(1, table.transitions.size)
        assertEquals(2, table.transitions["from"]?.size)
    }

    @Test
    fun addWithVarargToShouldAddMultipleTransitions() {
        val builder = BTransitionTable.Builder<String>()
        builder.add("from", To("to1"), To("to2"), To("to3"))

        val table = builder.build()
        assertEquals(1, table.transitions.size)
        assertEquals(3, table.transitions["from"]?.size)
    }

    @Test
    fun addWhenDuplicateTransitionShouldThrowException() {
        val builder = BTransitionTable.Builder<String>()
        val transition = BTransition("from", "to")
        builder.add(transition)

        assertThrows(DuplicateTransitionException::class.java) {
            builder.add(transition)
        }
    }

    @Test
    fun autoTransitionEnabledShouldSetValue() {
        val builder = BTransitionTable.Builder<String>()
        builder.autoTransitionEnabled(true)

        val table = builder.build()
        assertEquals(true, table.autoTransitionEnabled)
    }

    @Test
    fun fromBuilderShouldCreateToBuilder() {
        val builder = BTransitionTable.Builder<String>()
        val fromBuilder = builder.from("from")
        val toBuilder = fromBuilder.to("to")

        assertNotNull(toBuilder)
    }

    @Test
    fun fromBuilderShouldCreateToMultipleBuilder() {
        val builder = BTransitionTable.Builder<String>()
        val fromBuilder = builder.from("from")
        val toMultipleBuilder = fromBuilder.toMultiple()

        assertNotNull(toMultipleBuilder)
    }

    @Test
    fun toBuilderShouldAllowChainingMethods() {
        val builder = BTransitionTable.Builder<String>()
        val result = builder.from("from")
            .to("to")
            .condition { true }
            .action { }
            .postAction { }
            .end()

        assertEquals(builder, result)
    }

    @Test
    fun toMultipleBuilderShouldAllowAddingMultipleTransitions() {
        val builder = BTransitionTable.Builder<String>()
        builder.from("from")
            .toMultiple()
            .to("to1").end()
            .to("to2").end()
            .endMultiple()

        val table = builder.build()
        assertEquals(1, table.transitions.size)
        assertEquals(2, table.transitions["from"]?.size)
    }

    @Test
    fun buildShouldWarnWhenUnguardedAutoTransitionHidesLaterBranch() {
        val messages = collectDiagnosticMessages {
            BTransitionTable.Builder<String>()
                .from("processing")
                .toMultiple()
                .to("approved").end()
                .to("manual-review").condition { true }.end()
                .endMultiple()
                .build()
        }

        assertTrue(
            messages.any {
                it.contains("Ambiguous transition order from [processing]") &&
                    it.contains("unguarded transition to [approved]") &&
                    it.contains("transition to [manual-review]")
            }
        )
    }

    @Test
    fun buildShouldNotWarnWhenUnguardedAutoTransitionIsLastFallback() {
        val messages = collectDiagnosticMessages {
            BTransitionTable.Builder<String>()
                .from("processing")
                .toMultiple()
                .to("manual-review").condition { true }.end()
                .to("approved").condition { true }.end()
                .to("rejected").end()
                .endMultiple()
                .build()
        }

        assertTrue(messages.none { it.contains("Ambiguous transition order") })
    }

    @Test
    fun toBuilderEndShouldSnapshotMutableBuilderLists() {
        val builder = BTransitionTable.Builder<String>()
        val toBuilder = builder.from("from")
            .to("to")
            .condition { true }
            .action { }
            .postAction { }
        toBuilder.end()

        toBuilder
            .condition { false }
            .action { }
            .postAction { }

        val transition = builder.build().transitions["from"]!!.single()

        assertEquals(1, transition.to.conditions.size)
        assertEquals(1, transition.to.actions.size)
        assertEquals(1, transition.to.postActions.size)
    }

    @Test
    fun addWithToObjectShouldSnapshotMutableLists() {
        val conditions = mutableListOf<io.github.ngirchev.fsm.Guard<in StateContext<String>>>(
            io.github.ngirchev.fsm.Guard { true },
        )
        val actions = mutableListOf<io.github.ngirchev.fsm.Action<in StateContext<String>>>(
            io.github.ngirchev.fsm.Action { },
        )
        val postActions = mutableListOf<io.github.ngirchev.fsm.Action<in StateContext<String>>>(
            io.github.ngirchev.fsm.Action { },
        )
        val to = To("to", conditions, actions, postActions)

        val table = BTransitionTable.Builder<String>()
            .add("from", to)
            .build()

        conditions.add(io.github.ngirchev.fsm.Guard { false })
        actions.add(io.github.ngirchev.fsm.Action { })
        postActions.add(io.github.ngirchev.fsm.Action { })

        val transition = table.transitions["from"]!!.single()
        assertEquals(1, transition.to.conditions.size)
        assertEquals(1, transition.to.actions.size)
        assertEquals(1, transition.to.postActions.size)
    }

    @Test
    fun getAutoTransitionShouldReturnTransitionWhenConditionIsTrue() {
        val table = BTransitionTable.Builder<String>()
            .add("from", To("to", condition = { it.state == "from" }))
            .build()

        val context = SimpleStateContext("from")
        val transition = table.getAutoTransition(context)

        assertNotNull(transition)
        val t = transition!!
        assertEquals("from", t.from)
        assertEquals("to", t.to.state)
    }

    @Test
    fun getAutoTransitionShouldReturnNullWhenConditionIsFalse() {
        val table = BTransitionTable.Builder<String>()
            .add("from", To("to", condition = { it.state == "other" }))
            .build()

        val context = SimpleStateContext("from")
        val transition = table.getAutoTransition(context)

        assertNull(transition)
    }

    @Test
    fun createFsmShouldCreateBFsmInstance() {
        val table = BTransitionTable.Builder<String>()
            .add("from", "to")
            .build()

        val fsm = table.createFsm("initial")

        assertNotNull(fsm)
        assertEquals("initial", fsm.getState())
    }

    @Test
    fun createDomainFsmShouldCreateBDomainFsmInstance() {
        val table = BTransitionTable.Builder<DocumentState>()
            .add(DocumentState.NEW, DocumentState.READY_FOR_SIGN)
            .build()

        val domainFsm = table.createDomainFsm<Document>()

        assertNotNull(domainFsm)
    }

    @Test
    fun createDomainFsmWithFactoryShouldCreateCustomDomainFsm() {
        val table = BTransitionTable.Builder<DocumentState>()
            .add(DocumentState.NEW, DocumentState.READY_FOR_SIGN)
            .build()

        val domainFsm = table.createDomainFsm(::FactoryBDomainFsm)

        assertEquals(FactoryBDomainFsm::class, domainFsm::class)
    }

    @Test
    fun createDomainFsmShouldUseAutoTransitionScheduler() {
        var scheduledTransitions = 0
        val scheduler = AutoTransitionScheduler<DocumentState> { _, _, runTransition ->
            scheduledTransitions++
            runTransition()
        }
        val table = BTransitionTable.Builder<DocumentState>()
            .autoTransitionEnabled(true)
            .autoTransitionScheduler(scheduler)
            .add(DocumentState.NEW, DocumentState.READY_FOR_SIGN)
            .add(DocumentState.READY_FOR_SIGN, DocumentState.SIGNED)
            .build()
        val domain = Document(state = DocumentState.NEW)

        val domainFsm = table.createDomainFsm<Document>()
        domainFsm.changeState(domain, DocumentState.READY_FOR_SIGN)

        assertEquals(DocumentState.SIGNED, domain.state)
        assertEquals(1, scheduledTransitions)
    }

    @Test
    fun toBuilderShouldAllowPerTransitionAutoTransitionScheduler() {
        val scheduler = AutoTransitionScheduler<DocumentState> { _, _, runTransition -> runTransition() }

        val table = BTransitionTable.Builder<DocumentState>()
            .from(DocumentState.NEW)
            .to(DocumentState.READY_FOR_SIGN)
            .scheduleWith(scheduler)
            .end()
            .build()

        val transition = table.transitions[DocumentState.NEW]?.single() ?: error("Expected transition")

        assertEquals(scheduler, transition.to.autoTransitionScheduler)
    }

    @Test
    fun addWithToObjectShouldCopyAutoTransitionScheduler() {
        val scheduler = AutoTransitionScheduler<DocumentState> { _, _, runTransition -> runTransition() }
        val transitionTo = To(DocumentState.READY_FOR_SIGN, autoTransitionScheduler = scheduler)

        val table = BTransitionTable.Builder<DocumentState>()
            .add(DocumentState.NEW, transitionTo)
            .build()

        val transition = table.transitions[DocumentState.NEW]?.single() ?: error("Expected transition")

        assertEquals(scheduler, transition.to.autoTransitionScheduler)
    }

    private fun collectDiagnosticMessages(block: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger(TransitionTableDiagnostics::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        return try {
            block()
            appender.list.map { it.formattedMessage }
        } finally {
            logger.detachAppender(appender)
        }
    }

    private class CustomBTransitionTable(
        transitions: Map<DocumentState, LinkedHashSet<BTransition<DocumentState>>>,
        autoTransitionEnabled: Boolean,
        autoTransitionScheduler: AutoTransitionScheduler<DocumentState>,
    ) : BTransitionTable<DocumentState>(transitions, autoTransitionEnabled, autoTransitionScheduler)

    private class FactoryBDomainFsm(
        transitionTable: BTransitionTable<DocumentState>,
        autoTransitionEnabled: Boolean,
        autoTransitionScheduler: AutoTransitionScheduler<DocumentState>,
    ) : BDomainFsm<Document, DocumentState>(transitionTable, autoTransitionEnabled, autoTransitionScheduler)
}
