package io.github.ngirchev.fsm.example.spring.task;

import io.github.ngirchev.fsm.Action;
import io.github.ngirchev.fsm.StateContext;
import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflow;
import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus;
import org.springframework.stereotype.Component;

import static io.github.ngirchev.fsm.example.spring.task.ExternalWorkflowTaskType.ADVANCE;

@Component
public class EnqueueNextWorkflowTaskAction implements Action<StateContext<ExternalWorkflowStatus>> {

    private final ExternalWorkflowTaskRepository repository;

    public EnqueueNextWorkflowTaskAction(ExternalWorkflowTaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public void invoke(StateContext<ExternalWorkflowStatus> context) {
        ExternalWorkflow workflow = (ExternalWorkflow) context;
        repository.save(new ExternalWorkflowTask(
                workflow.getId(),
                ADVANCE,
                workflow.getState(),
                workflow.getVersion()
        ));
    }
}
