package io.github.ngirchev.fsm.impl.basic

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import io.github.ngirchev.fsm.exception.DuplicateTransitionException
import io.github.ngirchev.fsm.To
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.it.document.Document
import io.github.ngirchev.fsm.it.document.DocumentState
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
}
