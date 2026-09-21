package io.github.ngirchev.fsm.example.spring.task;

import io.github.ngirchev.fsm.spring.task.FsmTaskStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ExternalWorkflowTaskRepository
        extends JpaRepository<ExternalWorkflowTask, Long>, FsmTaskStore<ExternalWorkflowTask> {

    @Override
    @Query(value = """
            SELECT *
            FROM external_workflow_task
            WHERE status = 'PENDING'
            ORDER BY id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<ExternalWorkflowTask> claimNextPending();

    @Override
    default void complete(ExternalWorkflowTask task) {
        task.complete();
    }

    long countByStatus(ExternalWorkflowTaskStatus status);
}
