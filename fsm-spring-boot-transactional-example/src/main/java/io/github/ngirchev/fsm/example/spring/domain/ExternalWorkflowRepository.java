package io.github.ngirchev.fsm.example.spring.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExternalWorkflowRepository extends JpaRepository<ExternalWorkflow, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select workflow from ExternalWorkflow workflow where workflow.id = :id")
    Optional<ExternalWorkflow> findByIdForUpdate(@Param("id") Long id);
}
