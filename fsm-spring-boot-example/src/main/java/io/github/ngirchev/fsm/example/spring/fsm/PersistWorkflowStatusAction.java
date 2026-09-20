package io.github.ngirchev.fsm.example.spring.fsm;

import io.github.ngirchev.fsm.Action;
import io.github.ngirchev.fsm.StateContext;
import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflow;
import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowRepository;
import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus;
import org.springframework.stereotype.Component;

@Component
public class PersistWorkflowStatusAction implements Action<StateContext<ExternalWorkflowStatus>> {

    private final ExternalWorkflowRepository repository;

    public PersistWorkflowStatusAction(ExternalWorkflowRepository repository) {
        this.repository = repository;
    }

    @Override
    public void invoke(StateContext<ExternalWorkflowStatus> context) {
        ExternalWorkflow workflow = (ExternalWorkflow) context;
        ExternalWorkflow persistedWorkflow = repository.saveAndFlush(workflow);
        workflow.synchronizeVersionFrom(persistedWorkflow);
    }
}
