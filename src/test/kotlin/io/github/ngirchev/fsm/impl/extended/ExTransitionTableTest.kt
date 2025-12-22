package io.github.ngirchev.fsm.impl.extended

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import io.github.ngirchev.fsm.exception.DuplicateTransitionException
import io.github.ngirchev.fsm.exception.FsmException
import io.github.ngirchev.fsm.Timeout
import io.github.ngirchev.fsm.To
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.it.document.Document
import io.github.ngirchev.fsm.it.document.DocumentState
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExTransitionTableTest {

    private class SimpleStateContext(override var state: String, override var currentTransition: io.github.ngirchev.fsm.Transition<String>? = null) : StateContext<String>

    @Test
    @DisplayName("Should add the transition when the transition is not added")
    fun addWhenTransitionIsNotAddedThenAddTheTransition() {
        val transition: ExTransition<String, String> = mock()
        val builder = ExTransitionTable.Builder<String, String>()
        builder.add(transition)
        assertEquals(expected = 1, actual = builder.transitions.size, message = "")
    }

    @Test
    @DisplayName("Should throw an exception when the transition is already added")
    fun addWhenTransitionIsAlreadyAddedThenThrowException() {
        val builder = ExTransitionTable.Builder<String, String>()
        val transition = ExTransition("from", "to", "event")
        builder.add(transition)

        val exception = assertThrows(DuplicateTransitionException::class.java) {
            builder.add(transition)
        }

        assertEquals("Can't add duplicate transition [from]->[to]", exception.message)
    }

    @Test
    @DisplayName("FromBuilder should throw exception when onEvent is called twice")
    fun fromBuilderOnEventWhenCalledTwiceThenThrowException() {
        val builder = ExTransitionTable.Builder<String, String>()
        val fromBuilder = builder.from("from")

        fromBuilder.onEvent("event1")

        val exception = assertThrows(FsmException::class.java) {
            fromBuilder.onEvent("event2")
        }

        assertEquals("Already has event", exception.message)
    }

    @Test
    @DisplayName("ToBuilder should throw exception when onEvent is called twice")
    fun toBuilderOnEventWhenCalledTwiceThenThrowException() {
        val builder = ExTransitionTable.Builder<String, String>()
        val toBuilder = builder.from("from").to("to")

        toBuilder.onEvent("event1")

        val exception = assertThrows(FsmException::class.java) {
            toBuilder.onEvent("event2")
        }

        assertEquals("Already has event", exception.message)
    }

    @Test
    @DisplayName("ToBuilder should throw exception when timeout is called twice")
    fun toBuilderTimeoutWhenCalledTwiceThenThrowException() {
        val builder = ExTransitionTable.Builder<String, String>()
        val toBuilder = builder.from("from").to("to")

        toBuilder.timeout(Timeout(1))

        val exception = assertThrows(FsmException::class.java) {
            toBuilder.timeout(Timeout(2))
        }

        assertEquals("Already has timeout", exception.message)
    }

    @Test
    @DisplayName("ToBuilder should allow chaining methods")
    fun toBuilderShouldAllowChainingMethods() {
        val builder = ExTransitionTable.Builder<String, String>()
        val result = builder.from("from")
            .to("to")
            .onEvent("event")
            .onCondition { true }
            .action { }
            .postAction { }
            .timeout(Timeout(1))
            .end()

        assertEquals(builder, result)
    }

    @Test
    @DisplayName("Should add multiple To transitions with vararg")
    fun addWithVarargToShouldAddMultipleTransitions() {
        val builder = ExTransitionTable.Builder<String, String>()
        builder.add("from", "event", To("to1"), To("to2"), To("to3"))

        val table = builder.build()
        assertEquals(1, table.transitions.size)
        assertEquals(3, table.transitions["from"]?.size)
    }

    @Test
    @DisplayName("Should throw exception when duplicate transition in vararg To")
    fun addWithVarargToWhenDuplicateThenThrowException() {
        val builder = ExTransitionTable.Builder<String, String>()
        val to = To("to")

        builder.add("from", "event", to)

        val exception = assertThrows(DuplicateTransitionException::class.java) {
            builder.add("from", "event", to, to)
        }

        assertNotNull(exception.message)
    }

    @Test
    @DisplayName("autoTransitionEnabled should set the value")
    fun autoTransitionEnabledShouldSetValue() {
        val builder = ExTransitionTable.Builder<String, String>()
        builder.autoTransitionEnabled(true)

        val table = builder.build()
        assertEquals(true, table.autoTransitionEnabled)
    }

    @Test
    @DisplayName("getTransitionByEvent should return transition when event matches")
    fun getTransitionByEventWhenEventMatchesThenReturnTransition() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", "event", "to")
            .build()

        val context = SimpleStateContext("from")
        val transition = table.getTransitionByEvent(context, "event")

        assertNotNull(transition)
        val t = transition!!
        assertEquals("from", t.from)
        assertEquals("to", t.to.state)
        assertEquals("event", t.event)
    }

    @Test
    @DisplayName("getTransitionByEvent should return null when event does not match")
    fun getTransitionByEventWhenEventDoesNotMatchThenReturnNull() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", "event1", "to")
            .build()

        val context = SimpleStateContext("from")
        val transition = table.getTransitionByEvent(context, "event2")

        assertNull(transition)
    }

    @Test
    @DisplayName("getAutoTransition should return transition when event is null")
    fun getAutoTransitionWhenEventIsNullThenReturnTransition() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", null, "to")
            .build()

        val context = SimpleStateContext("from")
        val transition = table.getAutoTransition(context)

        assertNotNull(transition)
        val t = transition!!
        assertEquals("from", t.from)
        assertEquals("to", t.to.state)
        assertNull(t.event)
    }

    @Test
    @DisplayName("getAutoTransition should return null when no auto transition exists")
    fun getAutoTransitionWhenNoAutoTransitionThenReturnNull() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", "event", "to")
            .build()

        val context = SimpleStateContext("from")
        val transition = table.getAutoTransition(context)

        assertNull(transition)
    }

    @Test
    @DisplayName("createFsm should create ExFsm instance")
    fun createFsmShouldCreateExFsmInstance() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", "event", "to")
            .build()

        val fsm = table.createFsm("initial")

        assertNotNull(fsm)
        assertEquals("initial", fsm.getState())
    }

    @Test
    @DisplayName("createDomainFsm should create ExDomainFsm instance")
    fun createDomainFsmShouldCreateExDomainFsmInstance() {
        val table = ExTransitionTable.Builder<DocumentState, String>()
            .add(DocumentState.NEW, "event", DocumentState.READY_FOR_SIGN)
            .build()

        val domainFsm = table.createDomainFsm<Document>()

        assertNotNull(domainFsm)
    }

    @Test
    @DisplayName("getTransitionByEvent should return transition when condition is true")
    fun getTransitionByEventWhenConditionIsTrueThenReturnTransition() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", "event", "to", condition = { it.state == "from" })
            .build()

        val context = SimpleStateContext("from")
        val transition = table.getTransitionByEvent(context, "event")

        assertNotNull(transition)
    }

    @Test
    @DisplayName("getTransitionByEvent should return null when condition is false")
    fun getTransitionByEventWhenConditionIsFalseThenReturnNull() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", "event", "to", condition = { it.state == "other" })
            .build()

        val context = SimpleStateContext("from")
        val transition = table.getTransitionByEvent(context, "event")

        assertNull(transition)
    }

    @Test
    @DisplayName("getAutoTransition should return transition when condition is true")
    fun getAutoTransitionWhenConditionIsTrueThenReturnTransition() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", null, "to", condition = { it.state == "from" })
            .build()

        val context = SimpleStateContext("from")
        val transition = table.getAutoTransition(context)

        assertNotNull(transition)
    }

    @Test
    @DisplayName("getAutoTransition should return null when condition is false")
    fun getAutoTransitionWhenConditionIsFalseThenReturnNull() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", null, "to", condition = { it.state == "other" })
            .build()

        val context = SimpleStateContext("from")
        val transition = table.getAutoTransition(context)

        assertNull(transition)
    }

    @Test
    @DisplayName("add with condition, action, postAction, timeout should work")
    fun addWithAllParametersShouldWork() {
        var actionCalled = false
        var postActionCalled = false

        val table = ExTransitionTable.Builder<String, String>()
            .add(
                "from", "event", "to",
                condition = { true },
                action = { actionCalled = true },
                postAction = { postActionCalled = true },
                timeout = Timeout(1)
            )
            .build()

        assertEquals(1, table.transitions.size)
    }

    @Test
    @DisplayName("getTransitionByState should return transition when condition is true")
    fun getTransitionByStateWhenConditionIsTrueThenReturnTransition() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", null, "to", condition = { it.state == "from" })
            .build()

        val context = SimpleStateContext("from")
        val transition = table.getTransitionByState(context, "to")

        assertNotNull(transition)
        val t = transition!!
        assertEquals("from", t.from)
        assertEquals("to", t.to.state)
    }

    @Test
    @DisplayName("getTransitionByState should return null when condition is false")
    fun getTransitionByStateWhenConditionIsFalseThenReturnNull() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", null, "to", condition = { it.state == "other" })
            .build()

        val context = SimpleStateContext("from")
        val transition = table.getTransitionByState(context, "to")

        assertNull(transition)
    }

    @Test
    @DisplayName("getTransitionByState should return null when state does not match")
    fun getTransitionByStateWhenStateDoesNotMatchThenReturnNull() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", null, "to")
            .build()

        val context = SimpleStateContext("other")
        val transition = table.getTransitionByState(context, "to")

        assertNull(transition)
    }

    @Test
    @DisplayName("getTransitionByState should return null when target state does not match")
    fun getTransitionByStateWhenTargetStateDoesNotMatchThenReturnNull() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", null, "to1")
            .build()

        val context = SimpleStateContext("from")
        val transition = table.getTransitionByState(context, "to2")

        assertNull(transition)
    }

    @Test
    @DisplayName("getTransitionByEvent should return null when state does not match")
    fun getTransitionByEventWhenStateDoesNotMatchThenReturnNull() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", "event", "to")
            .build()

        val context = SimpleStateContext("other")
        val transition = table.getTransitionByEvent(context, "event")

        assertNull(transition)
    }

    @Test
    @DisplayName("getAutoTransition should return null when state does not match")
    fun getAutoTransitionWhenStateDoesNotMatchThenReturnNull() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", null, "to")
            .build()

        val context = SimpleStateContext("other")
        val transition = table.getAutoTransition(context)

        assertNull(transition)
    }

    @Test
    @DisplayName("add with To object should work")
    fun addWithToObjectShouldWork() {
        val builder = ExTransitionTable.Builder<String, String>()
        val to = To("to", condition = { true })
        builder.add("from", "event", to)

        val table = builder.build()
        assertEquals(1, table.transitions.size)
    }

    @Test
    @DisplayName("getTransitionByEvent should return first matching transition when multiple transitions exist")
    fun getTransitionByEventWhenMultipleTransitionsExistShouldReturnFirstMatching() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", "event", "to1", condition = { false })
            .add("from", "event", "to2", condition = { true })
            .add("from", "event", "to3", condition = { true })
            .build()

        val context = SimpleStateContext("from")
        val transition = table.getTransitionByEvent(context, "event")

        assertNotNull(transition)
        val t = transition!!
        assertEquals("to2", t.to.state)
    }

    @Test
    @DisplayName("getAutoTransition should return first matching transition when multiple transitions exist")
    fun getAutoTransitionWhenMultipleTransitionsExistShouldReturnFirstMatching() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", null, "to1", condition = { false })
            .add("from", null, "to2", condition = { true })
            .add("from", null, "to3", condition = { true })
            .build()

        val context = SimpleStateContext("from")
        val transition = table.getAutoTransition(context)

        assertNotNull(transition)
        val t = transition!!
        assertEquals("to2", t.to.state)
    }

    @Test
    @DisplayName("getTransitionByState should return first matching transition when multiple transitions exist")
    fun getTransitionByStateWhenMultipleTransitionsExistShouldReturnFirstMatching() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", null, "to", condition = { false })
            .add("from", null, "to", condition = { true })
            .build()

        val context = SimpleStateContext("from")
        val transition = table.getTransitionByState(context, "to")

        assertNotNull(transition)
        val t = transition!!
        assertEquals("to", t.to.state)
    }
}