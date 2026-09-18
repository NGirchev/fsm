package io.github.ngirchev.fsm

import io.github.ngirchev.fsm.serialization.ToDto
import io.github.ngirchev.fsm.serialization.TimeoutDto
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class CopySchedulingCompatibilityTest {
    @Test
    fun builderKeepsOldNamedCallsAndEnabledOnlySettings() {
        val ordinary = ExTransitionTable.Builder<String, String>()
            .add(from = "from", to = "to")
            .build()
        assertFalse(ordinary.transitions.getValue("from").first().to.autoTransitionEnabled)
        val automatic = ExTransitionTable.Builder<String, String>()
            .add(from = "from", to = "to", autoTransitionEnabled = true)
            .build()
        assertTrue(automatic.transitions.getValue("from").first().to.autoTransitionEnabled)
    }

    @Test
    fun localAutoFlagWithoutSchedulerSurvivesCopyAndDoesNotAffectEquality() {
        val automatic = To("to", autoTransitionScheduler = null, autoTransitionEnabled = true)
        val ordinary = To("to")
        val copied = automatic.copy()
        assertTrue(copied.autoTransitionEnabled)
        assertEquals(null, copied.autoTransitionScheduler)
        assertEquals(ordinary, copied)
        assertEquals(ordinary.hashCode(), copied.hashCode())
        val dto = ToDto("to", emptyList(), emptyList(), emptyList(), null, null, true)
        assertTrue(dto.copy().autoTransitionEnabled)
    }

    @Test
    fun transitionCopyRetainsLocalAutoScheduling() {
        val scheduler = NamedAutoTransitionScheduler<String>("deferred") { _, _, _ -> }
        val target = To("old", autoTransitionScheduler = scheduler)
        val copied = target.copy(state = "new")
        assertEquals("new", copied.state)
        assertTrue(copied.autoTransitionEnabled)
        assertSame(scheduler, copied.autoTransitionScheduler)
        val (state, conditions, actions, postActions, timeout) = copied
        assertEquals("new", state)
        assertEquals(emptyList(), conditions)
        assertEquals(emptyList(), actions)
        assertEquals(emptyList(), postActions)
        assertEquals(null, timeout)
    }

    @Test
    fun dtoCopyRetainsLocalAutoScheduling() {
        val target = ToDto("old", emptyList(), emptyList(), emptyList(), null, "deferred", true)
        val copied = target.copy(state = "new")
        assertEquals("new", copied.state)
        assertTrue(copied.autoTransitionEnabled)
        assertEquals("deferred", copied.autoTransitionScheduler)
    }

    @Test
    fun dtoRetainsDeclarativeValueSemanticsAndComponents() {
        val original = ToDto("state", listOf("guard"), listOf("action"), listOf("post"), TimeoutDto(1, "SECONDS"))
        assertEquals(original, original)
        assertFalse(original.equals(null))
        assertFalse(original.equals("state"))
        assertEquals(original, original.copy())
        assertEquals(original.hashCode(), original.copy().hashCode())
        assertNotEquals(original, original.copy(state = "other"))
        assertNotEquals(original, original.copy(conditions = emptyList()))
        assertNotEquals(original, original.copy(actions = emptyList()))
        assertNotEquals(original, original.copy(postActions = emptyList()))
        assertNotEquals(original, original.copy(timeout = null))
        val (state, guards, actions, posts, timeout) = original
        assertEquals("state", state)
        assertEquals(listOf("guard"), guards)
        assertEquals(listOf("action"), actions)
        assertEquals(listOf("post"), posts)
        assertEquals(TimeoutDto(1, "SECONDS"), timeout)
        assertEquals("ToDto(state=state, conditions=[guard], actions=[action], postActions=[post], timeout=TimeoutDto(value=1, unit=SECONDS))", original.toString())
        val empty = original.copy("new", emptyList(), emptyList(), emptyList(), null)
        assertEquals(ToDto("new", emptyList(), emptyList(), emptyList(), null).hashCode(), empty.hashCode())
        empty.autoTransitionEnabled = true
        empty.autoTransitionScheduler = "deferred"
        assertEquals(ToDto("new", emptyList(), emptyList(), emptyList(), null), empty)
    }

    @Test
    fun targetRetainsNullableStateAndCopyValueSemantics() {
        val nullable = To<String?>(null)
        assertEquals(nullable, nullable.copy())
        assertEquals(nullable.hashCode(), nullable.copy().hashCode())
        assertFalse(nullable.equals("other"))
        val full = nullable.copy("new", emptyList(), emptyList(), emptyList(), Timeout(1, TimeUnit.SECONDS))
        assertEquals("new", full.state)
        assertEquals(Timeout(1, TimeUnit.SECONDS), full.timeout)
    }
}
