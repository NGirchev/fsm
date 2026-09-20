package io.github.ngirchev.fsm.example.spring.domain;

public enum ExternalWorkflowStatus {
    NEW,
    AWAITING_EXTERNAL_SERVICE_RESULT,
    EXTERNAL_SERVICE_FAILED,
    EXTERNAL_SERVICE_DONE,
    NOTIFY,
    END
}
