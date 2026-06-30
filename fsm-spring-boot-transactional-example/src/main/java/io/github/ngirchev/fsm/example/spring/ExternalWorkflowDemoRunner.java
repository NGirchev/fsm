package io.github.ngirchev.fsm.example.spring;

import io.github.ngirchev.fsm.example.spring.service.ExternalWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fsm.example.runner", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExternalWorkflowDemoRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ExternalWorkflowDemoRunner.class);

    private final ExternalWorkflowService service;

    public ExternalWorkflowDemoRunner(ExternalWorkflowService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        Long workflowId = service.createWorkflow();
        service.start(workflowId);
        log.info("Workflow {} status history: {}", workflowId, service.statusHistory(workflowId));
    }
}
