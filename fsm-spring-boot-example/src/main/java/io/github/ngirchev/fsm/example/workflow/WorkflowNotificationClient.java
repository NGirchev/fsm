package io.github.ngirchev.fsm.example.workflow;

@FunctionalInterface
public interface WorkflowNotificationClient {
    // Called during NOTIFY -> END; a real client must deduplicate retries by idempotencyKey.
    void notify(ExternalWorkflow workflow, String idempotencyKey);
}
