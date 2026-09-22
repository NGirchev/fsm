package io.github.ngirchev.fsm.example.workflow;

import io.github.ngirchev.fsm.spring.worker.FsmTaskStore;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExternalWorkflowRepository
        extends JpaRepository<ExternalWorkflow, Long>, FsmTaskStore<ExternalWorkflow> {

    // start() locks the workflow so concurrent callers cannot start it twice.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select workflow from ExternalWorkflow workflow where workflow.id = :id")
    Optional<ExternalWorkflow> findByIdForUpdate(@Param("id") Long id);

    // Called inside processNext(); skips rows already locked by another worker.
    @Override
    @Query(value = """
            SELECT * FROM notification_workflow
            WHERE state = 'NOTIFY'
            ORDER BY id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<ExternalWorkflow> claimNextPending();

    // Called after the handler succeeds; END removes the row from the next poll.
    @Override
    default void complete(ExternalWorkflow workflow) {
        save(workflow);
    }
}
