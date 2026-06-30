package io.github.ngirchev.fsm.example.spring.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalWorkflowRepository extends JpaRepository<ExternalWorkflow, Long> {
}
