package io.github.ngirchev.fsm.spring.worker

import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Claims, handles and completes one task in a single transaction.
 * A handler failure skips completion and rolls back the transaction.
 */
open class FsmTaskProcessor<T>(
    private val store: FsmTaskStore<T>,
    private val handler: FsmTaskHandler<T>,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun processNext(): Boolean {
        val task = store.claimNextPending().orElse(null) ?: return false
        handler.handle(task)
        store.complete(task)
        return true
    }
}
