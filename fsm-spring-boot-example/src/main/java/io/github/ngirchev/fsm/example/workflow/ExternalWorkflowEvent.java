package io.github.ngirchev.fsm.example.workflow;

public enum ExternalWorkflowEvent {
    // Sent by ExternalWorkflowService.start() in the caller's transaction.
    START,
    // Sent by the background processor after claiming a NOTIFY workflow.
    ADVANCE
}
