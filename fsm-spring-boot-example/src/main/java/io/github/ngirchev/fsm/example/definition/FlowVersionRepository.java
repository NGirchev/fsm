package io.github.ngirchev.fsm.example.definition;

import io.github.ngirchev.fsm.spring.definition.FlowVersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface FlowVersionRepository extends JpaRepository<FlowVersionEntity, Long> {
    Optional<FlowVersionEntity> findTopByFlowKeyOrderByVersionDesc(String flowKey);

    Optional<FlowVersionEntity> findByFlowKeyAndVersionAndDeletedFalse(String flowKey, int version);

    Optional<FlowVersionEntity> findByFlowKeyAndStatusAndDeletedFalse(String flowKey, FlowVersionStatus status);

    List<FlowVersionEntity> findByFlowKeyAndDeletedFalseOrderByVersionDesc(String flowKey);
}
