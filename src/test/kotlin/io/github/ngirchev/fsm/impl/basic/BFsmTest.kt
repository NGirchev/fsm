package io.github.ngirchev.fsm.impl.basic

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.StateChangeListener
import io.github.ngirchev.fsm.AutoTransitionScheduler
import io.github.ngirchev.fsm.Timeout
import io.github.ngirchev.fsm.exception.FsmTransitionFailedException
import io.github.ngirchev.fsm.exception.FsmException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BFsmTest {

    private class SimpleStateContext(override var state: String, override var currentTransition: io.github.ngirchev.fsm.Transition<String>? = null) : StateContext<String>

    private class TrackingReadStateContext(initialState: String) : StateContext<String> {
        private var stateValue = initialState
        private val activeReads = AtomicInteger()

        val readersEntered = CountDownLatch(2)
        val releaseReaders = CountDownLatch(1)
        val maxActiveReads = AtomicInteger()
        override var currentTransition: io.github.ngirchev.fsm.Transition<String>? = null

        override var state: String
            get() {
                readersEntered.countDown()
                releaseReaders.await(1, TimeUnit.SECONDS)
                val active = activeReads.incrementAndGet()
                maxActiveReads.updateAndGet { current -> maxOf(current, active) }
                Thread.sleep(50)
                activeReads.decrementAndGet()
                return stateValue
            }
            set(value) {
                stateValue = value
            }
    }

    @Test
    fun constructorWithStateAndNullAutoTransitionShouldUseTableValue() {
        val table = BTransitionTable.Builder<String>()
            .add("from", "to")
            .autoTransitionEnabled(true)
            .build()

        val fsm = BFsm("initial", table, autoTransitionEnabled = null)

        assertEquals("initial", fsm.getState())
        assertEquals(true, fsm.autoTransitionEnabled)
    }

    @Test
    fun constructorWithContextAndNullAutoTransitionShouldUseTableValue() {
        val table = BTransitionTable.Builder<String>()
            .add("from", "to")
            .autoTransitionEnabled(false)
            .build()

        val context = SimpleStateContext("initial")
        val fsm = BFsm(context, table, autoTransitionEnabled = null)

        assertEquals("initial", fsm.getState())
        assertEquals(false, fsm.autoTransitionEnabled)
    }

    @Test
    fun toStateWhenTransitionExistsShouldChangeState() {
        val table = BTransitionTable.Builder<String>()
            .add("from", "to")
            .build()

        val fsm = BFsm("from", table)
        fsm.toState("to")

        assertEquals("to", fsm.getState())
    }

    @Test
    fun toStateWhenTransitionDoesNotExistShouldThrowException() {
        val table = BTransitionTable.Builder<String>()
            .add("from", "to")
            .build()

        val fsm = BFsm("from", table)

        assertThrows(FsmTransitionFailedException::class.java) {
            fsm.toState("nonexistent")
        }
    }

    @Test
    fun toStateWhenFromStateDoesNotMatchShouldThrowException() {
        val table = BTransitionTable.Builder<String>()
            .add("from", "to")
            .build()

        val fsm = BFsm("wrong", table)

        assertThrows(FsmException::class.java) {
            fsm.toState("to")
        }
    }

    @Test
    fun toStateWithAutoTransitionEnabledShouldPerformAutoTransitions() {
        val table = BTransitionTable.Builder<String>()
            .autoTransitionEnabled(true)
            .add("from", "intermediate")
            .add("intermediate", "to")
            .build()

        val fsm = BFsm("from", table, autoTransitionEnabled = true)
        fsm.toState("intermediate")

        assertEquals("to", fsm.getState())
    }

    @Test
    fun toStateWithAutoTransitionDisabledShouldNotPerformAutoTransitions() {
        val table = BTransitionTable.Builder<String>()
            .autoTransitionEnabled(false)
            .add("from", "intermediate")
            .add("intermediate", "to")
            .build()

        val fsm = BFsm("from", table, autoTransitionEnabled = false)
        fsm.toState("intermediate")

        assertEquals("intermediate", fsm.getState())
    }

    @Test
    fun toStateWithTimeoutShouldWait() {
        val start = System.currentTimeMillis()
        val table = BTransitionTable.Builder<String>()
            .add("from", io.github.ngirchev.fsm.To("to", timeout = io.github.ngirchev.fsm.Timeout(1)))
            .build()

        val fsm = BFsm("from", table)
        fsm.toState("to")
        val end = System.currentTimeMillis()

        assertEquals("to", fsm.getState())
        assertTrue(end - start >= 1000)
    }

    @Test
    fun toStateWithActionsShouldExecuteActions() {
        var actionCalled = false
        var postActionCalled = false

        val table = BTransitionTable.Builder<String>()
            .from("from")
            .to("to")
            .action { actionCalled = true }
            .postAction { postActionCalled = true }
            .end()
            .build()

        val fsm = BFsm("from", table)
        fsm.toState("to")

        assertEquals("to", fsm.getState())
        assertTrue(actionCalled)
        assertTrue(postActionCalled)
    }

    @Test
    fun toStateWhenActionFailsShouldReleaseWriteLockForNextTransition() {
        val table = BTransitionTable.Builder<String>()
            .from("from")
            .to("broken")
            .action { throw IllegalStateException("boom") }
            .end()
            .add("from", "recovered")
            .build()
        val fsm = BFsm("from", table)
        val executor = Executors.newSingleThreadExecutor()

        try {
            assertThrows(IllegalStateException::class.java) {
                fsm.toState("broken")
            }

            val recovery = executor.submit<String> {
                fsm.toState("recovered")
                fsm.getState()
            }

            assertEquals("recovered", recovery.get(1, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun toStateShouldNotifyListenersWhenPostActionFailsAfterStateChange() {
        val table = BTransitionTable.Builder<String>()
            .from("from")
            .to("to")
            .postAction { throw FsmException("post action failed") }
            .end()
            .build()
        val fsm = BFsm("from", table)
        var listenerCalled = false
        var capturedOldState: String? = null
        var capturedNewState: String? = null

        fsm.addStateChangeListener { _, oldState, newState ->
            listenerCalled = true
            capturedOldState = oldState
            capturedNewState = newState
        }

        assertThrows(FsmException::class.java) {
            fsm.toState("to")
        }

        assertEquals("to", fsm.getState())
        assertTrue(listenerCalled)
        assertEquals("from", capturedOldState)
        assertEquals("to", capturedNewState)
    }

    @Test
    fun toStateWithNullTimeoutShouldNotWait() {
        val start = System.currentTimeMillis()
        val table = BTransitionTable.Builder<String>()
            .add("from", "to")
            .build()

        val fsm = BFsm("from", table)
        fsm.toState("to")
        val end = System.currentTimeMillis()

        assertEquals("to", fsm.getState())
        assertTrue(end - start < 1000)
    }

    @Test
    fun toStateWithMillisecondsTimeoutShouldUseTimeoutUnit() {
        val start = System.currentTimeMillis()
        val table = BTransitionTable.Builder<String>()
            .add("from", io.github.ngirchev.fsm.To("to", timeout = Timeout(50, TimeUnit.MILLISECONDS)))
            .build()

        val fsm = BFsm("from", table)
        fsm.toState("to")
        val end = System.currentTimeMillis()

        assertEquals("to", fsm.getState())
        assertTrue(end - start >= 50)
        assertTrue(end - start < 1000)
    }

    @Test
    fun toStateWithMultipleAutoTransitionsShouldPerformAll() {
        val table = BTransitionTable.Builder<String>()
            .autoTransitionEnabled(true)
            .add("from", "intermediate1")
            .add("intermediate1", "intermediate2")
            .add("intermediate2", "to")
            .build()

        val fsm = BFsm("from", table, autoTransitionEnabled = true)
        fsm.toState("intermediate1")

        assertEquals("to", fsm.getState())
    }

    @Test
    fun defaultAutoTransitionSchedulerShouldHandleLongAutoTransitionChainWithoutStackOverflow() {
        val transitionCount = 10_000
        val builder = BTransitionTable.Builder<Int>()
            .autoTransitionEnabled(true)
            .add(0, 1)

        for (state in 1 until transitionCount) {
            builder.add(state, state + 1)
        }

        val fsm = BFsm(0, builder.build(), autoTransitionEnabled = true)
        fsm.toState(1)

        assertEquals(transitionCount, fsm.getState())
    }

    @Test
    fun addStateChangeListenerShouldBeCalledOnStateChange() {
        val table = BTransitionTable.Builder<String>()
            .add("from", "to")
            .build()

        val fsm = BFsm("from", table)
        var listenerCalled = false
        var capturedOldState: String? = null
        var capturedNewState: String? = null

        val listener = StateChangeListener<String> { _, oldState, newState ->
            listenerCalled = true
            capturedOldState = oldState
            capturedNewState = newState
        }

        fsm.addStateChangeListener(listener)
        fsm.toState("to")

        assertTrue(listenerCalled)
        assertEquals("from", capturedOldState)
        assertEquals("to", capturedNewState)
    }

    @Test
    fun removeStateChangeListenerShouldNotBeCalledAfterRemoval() {
        val table = BTransitionTable.Builder<String>()
            .add("from", "to")
            .build()

        val fsm = BFsm("from", table)
        var listenerCalled = false

        val listener = StateChangeListener<String> { _, _, _ ->
            listenerCalled = true
        }

        fsm.addStateChangeListener(listener)
        fsm.removeStateChangeListener(listener)
        fsm.toState("to")

        assertTrue(!listenerCalled)
    }

    @Test
    fun multipleStateChangeListenersShouldAllBeCalled() {
        val table = BTransitionTable.Builder<String>()
            .add("from", "to")
            .build()

        val fsm = BFsm("from", table)
        var listener1Called = false
        var listener2Called = false

        val listener1 = StateChangeListener<String> { _, _, _ ->
            listener1Called = true
        }
        val listener2 = StateChangeListener<String> { _, _, _ ->
            listener2Called = true
        }

        fsm.addStateChangeListener(listener1)
        fsm.addStateChangeListener(listener2)
        fsm.toState("to")

        assertTrue(listener1Called)
        assertTrue(listener2Called)
    }

    @Test
    fun concurrentTransitionsShouldNotExecuteActionsInParallelForSameFsm() {
        val activeActions = AtomicInteger()
        val maxActiveActions = AtomicInteger()
        val table = BTransitionTable.Builder<String>()
            .from("pending")
            .to("approved")
            .action { trackActionOverlap(activeActions, maxActiveActions) }
            .end()
            .from("pending")
            .to("rejected")
            .action { trackActionOverlap(activeActions, maxActiveActions) }
            .end()
            .build()
        val fsm = BFsm("pending", table)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val succeeded = AtomicInteger()
        val failed = AtomicInteger()
        val executor = Executors.newFixedThreadPool(2)

        try {
            val futures = listOf("approved", "rejected").map { target ->
                executor.submit {
                    ready.countDown()
                    start.await()
                    try {
                        fsm.toState(target)
                        succeeded.incrementAndGet()
                    } catch (e: FsmException) {
                        failed.incrementAndGet()
                    }
                }
            }

            assertTrue(ready.await(1, TimeUnit.SECONDS))
            start.countDown()
            futures.forEach { it.get(1, TimeUnit.SECONDS) }

            assertEquals(1, succeeded.get())
            assertEquals(1, failed.get())
            assertEquals(1, maxActiveActions.get())
            assertTrue(fsm.getState() == "approved" || fsm.getState() == "rejected")
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun concurrentStateReadsShouldOverlapWhenNoTransitionIsRunning() {
        val table = BTransitionTable.Builder<String>()
            .add("from", "to")
            .build()
        val context = TrackingReadStateContext("from")
        val fsm = BFsm(context, table)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val futures = List(2) {
                executor.submit<String> {
                    fsm.getState()
                }
            }

            assertTrue(context.readersEntered.await(500, TimeUnit.MILLISECONDS))
            context.releaseReaders.countDown()
            futures.forEach { assertEquals("from", it.get(1, TimeUnit.SECONDS)) }

            assertEquals(2, context.maxActiveReads.get())
        } finally {
            context.releaseReaders.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun stateReadShouldWaitForRunningTransition() {
        val actionStarted = CountDownLatch(1)
        val releaseAction = CountDownLatch(1)
        val table = BTransitionTable.Builder<String>()
            .from("from")
            .to("to")
            .action {
                actionStarted.countDown()
                releaseAction.await(1, TimeUnit.SECONDS)
            }
            .end()
            .build()
        val fsm = BFsm("from", table)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val transitionFuture = executor.submit {
                fsm.toState("to")
            }
            assertTrue(actionStarted.await(1, TimeUnit.SECONDS))

            val readFuture = executor.submit<String> {
                fsm.getState()
            }
            Thread.sleep(50)
            assertTrue(!readFuture.isDone)

            releaseAction.countDown()
            transitionFuture.get(1, TimeUnit.SECONDS)
            assertEquals("to", readFuture.get(1, TimeUnit.SECONDS))
        } finally {
            releaseAction.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun scheduledAutoTransitionCallbackShouldNotDeadlockAndShouldBlockReads() {
        val actionStarted = CountDownLatch(1)
        val releaseAction = CountDownLatch(1)
        val scheduledCallbacks = mutableListOf<() -> Unit>()
        val scheduler = AutoTransitionScheduler<String> { _, _, runTransition ->
            scheduledCallbacks.add(runTransition)
        }
        val table = BTransitionTable.Builder<String>()
            .autoTransitionEnabled(true)
            .add("from", "intermediate")
            .from("intermediate")
            .to("to")
            .action {
                actionStarted.countDown()
                releaseAction.await(1, TimeUnit.SECONDS)
            }
            .end()
            .build()
        val fsm = BFsm("from", table, autoTransitionEnabled = true, autoTransitionScheduler = scheduler)
        val executor = Executors.newFixedThreadPool(2)

        fsm.toState("intermediate")

        try {
            assertEquals("intermediate", fsm.getState())
            assertEquals(1, scheduledCallbacks.size)

            val autoTransitionFuture = executor.submit {
                scheduledCallbacks.single().invoke()
            }
            assertTrue(actionStarted.await(1, TimeUnit.SECONDS))

            val readFuture = executor.submit<String> {
                fsm.getState()
            }
            Thread.sleep(50)
            assertTrue(!readFuture.isDone)

            releaseAction.countDown()
            autoTransitionFuture.get(1, TimeUnit.SECONDS)
            assertEquals("to", readFuture.get(1, TimeUnit.SECONDS))
        } finally {
            releaseAction.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun subclassShouldCustomizeTransitionExecutionAndAccessRuntimeState() {
        val scheduler = AutoTransitionScheduler<String> { _, _, runTransition -> runTransition() }
        val table = BTransitionTable.Builder<String>()
            .add("from", "to")
            .build()
        val fsm = CustomBFsm("from", table, scheduler)

        fsm.toState("to")

        assertEquals("to", fsm.getState())
        assertEquals("from", fsm.stateBeforeExecution)
        assertEquals("to", fsm.executedTransition?.to?.state)
        assertTrue(fsm.usesScheduler(scheduler))
    }

    private class CustomBFsm(
        state: String,
        transitionTable: BTransitionTable<String>,
        autoTransitionScheduler: AutoTransitionScheduler<String>,
    ) : BFsm<String>(state, transitionTable, autoTransitionScheduler = autoTransitionScheduler) {
        var stateBeforeExecution: String? = null
            private set
        var executedTransition: BTransition<String>? = null
            private set

        fun usesScheduler(scheduler: AutoTransitionScheduler<String>): Boolean = autoTransitionScheduler === scheduler

        override fun transitionExecution(transition: BTransition<String>) {
            stateBeforeExecution = context.state
            executedTransition = transition
            super.transitionExecution(transition)
        }
    }

    private fun trackActionOverlap(activeActions: AtomicInteger, maxActiveActions: AtomicInteger) {
        val active = activeActions.incrementAndGet()
        maxActiveActions.updateAndGet { current -> maxOf(current, active) }
        try {
            Thread.sleep(50)
        } finally {
            activeActions.decrementAndGet()
        }
    }
}
