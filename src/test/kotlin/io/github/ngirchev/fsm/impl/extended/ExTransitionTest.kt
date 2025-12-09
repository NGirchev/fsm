package io.github.ngirchev.fsm.impl.extended

import io.github.ngirchev.fsm.To
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ExTransitionTest {

    @Test
    fun equalsWhenSameInstanceThenReturnTrue() {
        val transition = ExTransition("from", "to", "event")
        assertTrue(transition == transition)
    }

    @Test
    fun equalsWhenNullThenReturnFalse() {
        val transition = ExTransition("from", "to", "event")
        val nullTransition: ExTransition<String, String>? = null
        assertFalse(transition == nullTransition)
    }

    @Test
    fun equalsWhenDifferentClassThenReturnFalse() {
        val transition = ExTransition("from", "to", "event")
        assertFalse(transition.equals("not a transition"))
    }

    @Test
    fun equalsWhenDifferentEventThenReturnFalse() {
        val transition1 = ExTransition("from", "to", "event1")
        val transition2 = ExTransition("from", "to", "event2")
        assertFalse(transition1 == transition2)
    }

    @Test
    fun equalsWhenSameEventThenReturnTrue() {
        val transition1 = ExTransition("from", "to", "event")
        val transition2 = ExTransition("from", "to", "event")
        assertTrue(transition1 == transition2)
    }

    @Test
    fun equalsWhenBothEventsNullThenReturnTrue() {
        val transition1 = ExTransition("from", "to", null)
        val transition2 = ExTransition("from", "to", null)
        assertTrue(transition1 == transition2)
    }

    @Test
    fun equalsWhenOneEventNullThenReturnFalse() {
        val transition1 = ExTransition("from", "to", "event")
        val transition2 = ExTransition("from", "to", null)
        assertFalse(transition1 == transition2)
    }

    @Test
    fun equalsWhenDifferentFromStateThenReturnFalse() {
        val transition1 = ExTransition("from1", "to", "event")
        val transition2 = ExTransition("from2", "to", "event")
        assertFalse(transition1 == transition2)
    }

    @Test
    fun equalsWhenDifferentToStateThenReturnFalse() {
        val transition1 = ExTransition("from", "to1", "event")
        val transition2 = ExTransition("from", "to2", "event")
        assertFalse(transition1 == transition2)
    }

    @Test
    fun hashCodeWhenSameTransitionThenReturnSameHashCode() {
        val transition1 = ExTransition("from", "to", "event")
        val transition2 = ExTransition("from", "to", "event")
        assertEquals(transition1.hashCode(), transition2.hashCode())
    }

    @Test
    fun hashCodeWhenDifferentEventThenReturnDifferentHashCode() {
        val transition1 = ExTransition("from", "to", "event1")
        val transition2 = ExTransition("from", "to", "event2")
        assertNotEquals(transition1.hashCode(), transition2.hashCode())
    }

    @Test
    fun hashCodeWhenOneEventNullThenReturnDifferentHashCode() {
        val transition1 = ExTransition("from", "to", "event")
        val transition2 = ExTransition("from", "to", null)
        assertNotEquals(transition1.hashCode(), transition2.hashCode())
    }

    @Test
    fun toStringShouldIncludeEvent() {
        val transition = ExTransition("from", "to", "event")
        val toString = transition.toString()
        assertTrue(toString.contains("event=event"))
    }

    @Test
    fun toStringWhenEventNullShouldIncludeNull() {
        val transition = ExTransition("from", "to", null)
        val toString = transition.toString()
        assertTrue(toString.contains("event=null"))
    }

    @Test
    fun constructorWithToObjectShouldWork() {
        val to = To("to")
        val transition = ExTransition("from", to, "event")
        assertEquals("from", transition.from)
        assertEquals("to", transition.to.state)
        assertEquals("event", transition.event)
    }

    @Test
    fun constructorWithToObjectAndNullEventShouldWork() {
        val to = To("to")
        val transition = ExTransition("from", to, null)
        assertEquals("from", transition.from)
        assertEquals("to", transition.to.state)
        assertEquals(null, transition.event)
    }

    @Test
    fun constructorWithStringToAndNullEventShouldWork() {
        val transition = ExTransition("from", "to", null)
        assertEquals("from", transition.from)
        assertEquals("to", transition.to.state)
        assertEquals(null, transition.event)
    }
}
