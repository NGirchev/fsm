package io.github.ngirchev.fsm.example.spring.task;

import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflow;
import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowEvent;
import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowRepository;
import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus;
import io.github.ngirchev.fsm.impl.extended.ExDomainFsm;
import io.github.ngirchev.fsm.spring.task.FsmTaskProcessor;
import org.springframework.stereotype.Service;

import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowEvent.ADVANCE;

@Service
public class ExternalWorkflowTaskProcessor extends FsmTaskProcessor<ExternalWorkflowTask> {

    public ExternalWorkflowTaskProcessor(
            ExternalWorkflowTaskRepository taskRepository,
            ExternalWorkflowRepository workflowRepository,
            ExDomainFsm<ExternalWorkflow, ExternalWorkflowStatus, ExternalWorkflowEvent> fsm
    ) {
        super(taskRepository, task -> process(task, workflowRepository, fsm));
    }

    private static void process(
            ExternalWorkflowTask task,
            ExternalWorkflowRepository workflowRepository,
            ExDomainFsm<ExternalWorkflow, ExternalWorkflowStatus, ExternalWorkflowEvent> fsm
    ) {
        ExternalWorkflow workflow = workflowRepository.findByIdForUpdate(task.getWorkflowId()).orElseThrow();
        if (workflow.getState() == task.getExpectedState()
                && workflow.getVersion() == task.getExpectedVersion()) {
            fsm.handle(workflow, eventFor(task.getTaskType()));
        }
    }

    private static ExternalWorkflowEvent eventFor(ExternalWorkflowTaskType taskType) {
        return switch (taskType) {
            case ADVANCE -> ADVANCE;
        };
    }
}
