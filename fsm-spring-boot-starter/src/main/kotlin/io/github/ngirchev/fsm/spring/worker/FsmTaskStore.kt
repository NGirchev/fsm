package io.github.ngirchev.fsm.spring.worker

import java.util.Optional

/**
 * Persistence port for durable FSM tasks.
 *
 * [claimNextPending] runs inside the processor transaction. Implementations must prevent another
 * worker from claiming the same task until that transaction finishes.
 */
interface FsmTaskStore<T> {
    fun claimNextPending(): Optional<T>

    fun complete(task: T)
}
