package io.github.ngirchev.fsm

/**
 * Strategy for running an auto-transition.
 *
 * Implementations come in two flavors:
 *  - **synchronous**: [schedule] invokes [runTransition] on the calling thread before returning
 *    ([runsSynchronously] == `true`). The FSM drives such schedulers iteratively, so a long
 *    chain of synchronous auto-transitions does not grow the call stack.
 *  - **deferred**: [schedule] arranges for [runTransition] to be invoked later, possibly on
 *    another thread ([runsSynchronously] == `false`). The FSM returns immediately after
 *    [schedule] and relies on the scheduler to eventually invoke [runTransition].
 *
 * Reentrancy contract: when [schedule] is called, the calling thread holds the FSM write lock
 * only for the synchronous case (and the lock is reentrant). Deferred schedulers must NOT
 * attempt to acquire any FSM lock from within [schedule] itself; locking is handled inside
 * [runTransition]. Deferred schedulers are responsible for thread-safety of any state they
 * capture between [schedule] and the eventual invocation of [runTransition].
 */
fun interface AutoTransitionScheduler<STATE> {
    /**
     * `true` when [schedule] invokes [runTransition] synchronously on the calling thread
     * before returning. The FSM uses this flag to drive immediate auto-transition chains
     * iteratively instead of recursing through the scheduler, which avoids stack overflow
     * on long chains.
     *
     * Default is `false`: custom schedulers are assumed to be deferred. Synchronous custom
     * schedulers must override this to `true` (or extend [ImmediateAutoTransitionScheduler]).
     */
    val runsSynchronously: Boolean
        get() = false

    fun schedule(
        context: StateContext<STATE>,
        transition: Transition<STATE>,
        runTransition: () -> Unit,
    )
}

/**
 * Scheduler that can be serialized by ID.
 */
interface IdentifiableAutoTransitionScheduler<STATE> : AutoTransitionScheduler<STATE> {
    val id: String?
}

/**
 * Default synchronous scheduler: invokes the transition inline on the calling thread.
 */
class ImmediateAutoTransitionScheduler<STATE> : AutoTransitionScheduler<STATE> {
    override val runsSynchronously: Boolean = true

    override fun schedule(
        context: StateContext<STATE>,
        transition: Transition<STATE>,
        runTransition: () -> Unit,
    ) {
        runTransition()
    }
}

/**
 * Named scheduler wrapper that provides a meaningful [toString] for debugging and logging,
 * analogous to [NamedAction] / [NamedGuard]. Use this when a custom scheduler would otherwise
 * render as a lambda address in logs or in the [To] data class toString.
 *
 * Example:
 * ```
 * val scheduler = NamedAutoTransitionScheduler("AfterCommitScheduler") { ctx, t, run -> transactionTemplate.afterCommit { run() } }
 * println(scheduler) // prints "AfterCommitScheduler"
 * ```
 */
open class NamedAutoTransitionScheduler<STATE>(
    private val name: String,
    private val delegate: AutoTransitionScheduler<STATE>,
) : IdentifiableAutoTransitionScheduler<STATE> {
    override val runsSynchronously: Boolean get() = delegate.runsSynchronously
    override val id: String? = name

    override fun schedule(
        context: StateContext<STATE>,
        transition: Transition<STATE>,
        runTransition: () -> Unit,
    ) = delegate.schedule(context, transition, runTransition)

    override fun toString(): String = name
}
