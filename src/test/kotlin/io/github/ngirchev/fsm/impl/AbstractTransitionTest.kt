package io.github.ngirchev.fsm.impl

import io.github.ngirchev.fsm.To
import io.github.ngirchev.fsm.impl.basic.BTransition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AbstractTransitionTest {

    @Test
    fun equalsWhenSameInstanceThenReturnTrue() {
        val transition = BTransition("from", "to")
        assertTrue(transition == transition)
    }

    @Test
    fun equalsWhenNullThenReturnFalse() {
        val transition = BTransition("from", "to")
        val nullTransition: BTransition<String>? = null
        assertFalse(transition == nullTransition)
    }

    @Test
    fun equalsWhenDifferentClassThenReturnFalse() {
        val transition = BTransition("from", "to")
        assertFalse(transition.equals("not a transition"))
    }

    @Test
    fun equalsWhenDifferentFromStateThenReturnFalse() {
        val transition1 = BTransition("from1", "to")
        val transition2 = BTransition("from2", "to")
        assertFalse(transition1 == transition2)
    }

    @Test
    fun equalsWhenDifferentToStateThenReturnFalse() {
        val transition1 = BTransition("from", "to1")
        val transition2 = BTransition("from", "to2")
        assertFalse(transition1 == transition2)
    }

    @Test
    fun equalsWhenDifferentToObjectThenReturnFalse() {
        val transition1 = BTransition("from", To("to1"))
        val transition2 = BTransition("from", To("to2"))
        assertFalse(transition1 == transition2)
    }

    @Test
    fun equalsWhenSameStatesThenReturnTrue() {
        val transition1 = BTransition("from", "to")
        val transition2 = BTransition("from", "to")
        assertTrue(transition1 == transition2)
    }

    @Test
    fun hashCodeWhenSameTransitionThenReturnSameHashCode() {
        val transition1 = BTransition("from", "to")
        val transition2 = BTransition("from", "to")
        assertEquals(transition1.hashCode(), transition2.hashCode())
    }

    @Test
    fun hashCodeWhenDifferentFromStateThenReturnDifferentHashCode() {
        val transition1 = BTransition("from1", "to")
        val transition2 = BTransition("from2", "to")
        assertNotEquals(transition1.hashCode(), transition2.hashCode())
    }

    @Test
    fun hashCodeWhenDifferentToStateThenReturnDifferentHashCode() {
        val transition1 = BTransition("from", "to1")
        val transition2 = BTransition("from", "to2")
        assertNotEquals(transition1.hashCode(), transition2.hashCode())
    }

    @Test
    fun hashCodeWhenFromIsNullThenReturnHashCode() {
        val transition = BTransition(null, "to")
        val hashCode = transition.hashCode()
        assertTrue(hashCode != 0)
    }

    @Test
    fun toStringShouldIncludeAllFields() {
        val transition = BTransition("from", "to")
        val toString = transition.toString()
        assertTrue(toString.contains("from=from"))
        assertTrue(toString.contains("to=to"))
        assertTrue(toString.contains("conditionsCount"))
        assertTrue(toString.contains("actionsCount"))
        assertTrue(toString.contains("postActionsCount"))
        assertTrue(toString.contains("timeout"))
    }

    @Test
    fun toStringWithToObjectShouldIncludeAllFields() {
        val to = To("to")
        val transition = BTransition("from", to)
        val toString = transition.toString()
        assertTrue(toString.contains("from=from"))
        assertTrue(toString.contains("to=to"))
    }
}
