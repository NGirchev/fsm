package io.github.ngirchev.fsm

import io.github.ngirchev.fsm.impl.basic.BFsm
import io.github.ngirchev.fsm.impl.basic.BTransitionTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertSame

class NamedAutoTransitionSchedulerTest {

    @Test
    fun toStringShouldReturnName() {
        val scheduler = NamedAutoTransitionScheduler<String>("AfterCommit") { _, _, run -> run() }
        assertEquals("AfterCommit", scheduler.toString())
        assertEquals("AfterCommit", scheduler.id)
    }

    @Test
    fun runsSynchronouslyShouldDelegateToWrappedScheduler() {
        val immediate = NamedAutoTransitionScheduler<String>("Immediate", ImmediateAutoTransitionScheduler())
        assertTrue(immediate.runsSynchronously)

        val deferred = NamedAutoTransitionScheduler<String>("Deferred") { _, _, _ -> /* no-op */ }
        assertFalse(deferred.runsSynchronously)
    }

    @Test
    fun scheduleShouldInvokeWrappedScheduler() {
        var invoked = false
        val wrapped = AutoTransitionScheduler<String> { _, _, runTransition -> invoked = true; runTransition() }
        val named = NamedAutoTransitionScheduler("Wrapped", wrapped)

        val table = BTransitionTable.Builder<String>()
            .autoTransitionEnabled(true)
            .from("from").to("intermediate").end()
            .from("intermediate").to("to").auto().deferWith(named).end()
            .build()
        val fsm = BFsm("from", table, autoTransitionEnabled = true)

        fsm.toState("intermediate")

        assertTrue(invoked)
        assertEquals("to", fsm.getState())
        assertSame(named, table.transitions["intermediate"]?.single()?.to?.autoTransitionScheduler)
    }
}
