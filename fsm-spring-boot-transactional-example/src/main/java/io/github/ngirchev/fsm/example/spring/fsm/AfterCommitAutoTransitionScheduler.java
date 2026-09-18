package io.github.ngirchev.fsm.example.spring.fsm;

import io.github.ngirchev.fsm.AutoTransitionScheduler;
import io.github.ngirchev.fsm.StateContext;
import io.github.ngirchev.fsm.Transition;
import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AfterCommitAutoTransitionScheduler implements AutoTransitionScheduler<ExternalWorkflowStatus> {

    private final TransactionTemplate transactionTemplate;

    public AfterCommitAutoTransitionScheduler(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void schedule(
            StateContext<ExternalWorkflowStatus> context,
            Transition<ExternalWorkflowStatus> transition,
            Function0<Unit> runTransition
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            runInNewTransaction(runTransition);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runInNewTransaction(runTransition);
            }
        });
    }

    private void runInNewTransaction(Function0<Unit> runTransition) {
        transactionTemplate.executeWithoutResult(status -> runTransition.invoke());
    }
}
