package io.github.ngirchev.fsm.spring.task;

import java.util.Optional;

/**
 * Persistence port for durable FSM tasks.
 *
 * <p>{@link #claimNextPending()} is called inside the processor transaction. Implementations must
 * prevent another worker from claiming the same task until that transaction finishes. The locking
 * mechanism is deliberately storage-specific.</p>
 */
public interface FsmTaskStore<T> {

    Optional<T> claimNextPending();

    void complete(T task);
}
