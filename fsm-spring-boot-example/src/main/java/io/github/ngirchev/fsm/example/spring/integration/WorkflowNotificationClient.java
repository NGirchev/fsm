package io.github.ngirchev.fsm.example.spring.integration;

import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflow;

public interface WorkflowNotificationClient {

    void notify(ExternalWorkflow workflow, String idempotencyKey);
}
