package io.github.ngirchev.fsm.example.spring.task;

import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "external_workflow_task",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_external_workflow_task_transition",
                columnNames = {"workflow_id", "task_type", "expected_state", "expected_version"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalWorkflowTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false)
    private Long workflowId;

    @Column(name = "expected_version", nullable = false)
    private long expectedVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private ExternalWorkflowTaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "expected_state", nullable = false)
    private ExternalWorkflowStatus expectedState;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExternalWorkflowTaskStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public ExternalWorkflowTask(
            Long workflowId,
            ExternalWorkflowTaskType taskType,
            ExternalWorkflowStatus expectedState,
            long expectedVersion
    ) {
        this.workflowId = workflowId;
        this.taskType = taskType;
        this.expectedState = expectedState;
        this.expectedVersion = expectedVersion;
        this.status = ExternalWorkflowTaskStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void complete() {
        status = ExternalWorkflowTaskStatus.COMPLETED;
        completedAt = Instant.now();
    }
}
