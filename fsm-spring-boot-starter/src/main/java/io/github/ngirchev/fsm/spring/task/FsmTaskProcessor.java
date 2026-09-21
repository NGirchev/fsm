package io.github.ngirchev.fsm.spring.task;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes one durable FSM task in the same transaction that marks it completed.
 *
 * <p>If task handling fails, completion is not attempted and the transaction is rolled back.</p>
 */
public class FsmTaskProcessor<T> {

    private final FsmTaskStore<T> store;
    private final FsmTaskHandler<T> handler;

    public FsmTaskProcessor(FsmTaskStore<T> store, FsmTaskHandler<T> handler) {
        this.store = store;
        this.handler = handler;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processNext() {
        return store.claimNextPending()
                .map(this::process)
                .orElse(false);
    }

    private boolean process(T task) {
        handler.handle(task);
        store.complete(task);
        return true;
    }
}
