package io.github.ngirchev.fsm.example.spring.service;

import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflow;
import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowEvent;
import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowRepository;
import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus;
import io.github.ngirchev.fsm.impl.extended.ExDomainFsm;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReaderFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowEvent.START;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.NEW;

@Service
public class ExternalWorkflowService {

    private final ExternalWorkflowRepository repository;
    private final ExDomainFsm<ExternalWorkflow, ExternalWorkflowStatus, ExternalWorkflowEvent> fsm;
    private final EntityManager entityManager;

    public ExternalWorkflowService(
            ExternalWorkflowRepository repository,
            ExDomainFsm<ExternalWorkflow, ExternalWorkflowStatus, ExternalWorkflowEvent> fsm,
            EntityManager entityManager
    ) {
        this.repository = repository;
        this.fsm = fsm;
        this.entityManager = entityManager;
    }

    @Transactional
    public Long createWorkflow() {
        return repository.saveAndFlush(new ExternalWorkflow(NEW)).getId();
    }

    @Transactional
    public void start(Long workflowId) {
        ExternalWorkflow workflow = repository.findById(workflowId).orElseThrow();
        fsm.handle(workflow, START);
    }

    @Transactional(readOnly = true)
    public ExternalWorkflowStatus currentStatus(Long workflowId) {
        return repository.findById(workflowId).orElseThrow().getState();
    }

    @Transactional(readOnly = true)
    public List<ExternalWorkflowStatus> statusHistory(Long workflowId) {
        return AuditReaderFactory.get(entityManager)
                .getRevisions(ExternalWorkflow.class, workflowId)
                .stream()
                .map(revision -> AuditReaderFactory.get(entityManager)
                        .find(ExternalWorkflow.class, workflowId, revision)
                        .getState())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Integer> statusRevisionNumbers(Long workflowId) {
        return AuditReaderFactory.get(entityManager)
                .getRevisions(ExternalWorkflow.class, workflowId)
                .stream()
                .map(Number::intValue)
                .toList();
    }
}
