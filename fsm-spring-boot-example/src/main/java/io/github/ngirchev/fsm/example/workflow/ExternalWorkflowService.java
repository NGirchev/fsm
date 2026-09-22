package io.github.ngirchev.fsm.example.workflow;

import io.github.ngirchev.fsm.impl.extended.ExDomainFsm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.github.ngirchev.fsm.example.workflow.ExternalWorkflowEvent.START;

@Service
@RequiredArgsConstructor
public class ExternalWorkflowService {

    // Loads and locks the workflow row used by the business operation.
    private final ExternalWorkflowRepository repository;
    // Rules from ExternalWorkflowConfiguration, applied to the loaded entity.
    private final ExDomainFsm<ExternalWorkflow, ExternalWorkflow.State, ExternalWorkflowEvent> fsm;

    // The demo runner calls this first; a NEW workflow is not picked up by the worker.
    @Transactional
    public Long createWorkflow() {
        return repository.save(new ExternalWorkflow()).getId();
    }

    // The demo runner starts the workflow; NOTIFY becomes visible to the worker after commit.
    @Transactional
    public void start(Long id) {
        ExternalWorkflow workflow = repository.findByIdForUpdate(id).orElseThrow();
        fsm.handle(workflow, START);
    }

    @Transactional(readOnly = true)
    public ExternalWorkflow.State currentStatus(Long id) {
        return repository.findById(id).orElseThrow().getState();
    }
}
