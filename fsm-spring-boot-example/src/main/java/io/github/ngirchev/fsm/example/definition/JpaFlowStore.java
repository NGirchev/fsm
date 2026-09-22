package io.github.ngirchev.fsm.example.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ngirchev.fsm.spring.definition.FlowDefinition;
import io.github.ngirchev.fsm.spring.definition.FlowStore;
import io.github.ngirchev.fsm.spring.definition.FlowVersion;
import io.github.ngirchev.fsm.spring.definition.FlowVersionStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class JpaFlowStore implements FlowStore {
    private final FlowVersionRepository versions;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    @Override
    public void lock(String flowKey) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:flowKey, 0))")
                .setParameter("flowKey", flowKey)
                .getSingleResult();
    }

    @Override
    public Optional<FlowVersion> latest(String flowKey) {
        return versions.findTopByFlowKeyOrderByVersionDesc(flowKey).map(this::toVersion);
    }

    @Override
    public List<FlowVersion> list(String flowKey) {
        return versions.findByFlowKeyAndDeletedFalseOrderByVersionDesc(flowKey).stream().map(this::toVersion).toList();
    }

    @Override
    public Optional<FlowVersion> get(String flowKey, int version) {
        return versions.findByFlowKeyAndVersionAndDeletedFalse(flowKey, version).map(this::toVersion);
    }

    @Override
    public Optional<FlowVersion> active(String flowKey) {
        return versions.findByFlowKeyAndStatusAndDeletedFalse(flowKey, FlowVersionStatus.ACTIVE).map(this::toVersion);
    }

    @Override
    public void deleteDraft(String flowKey, int version) {
        var stored = versions.findByFlowKeyAndVersionAndDeletedFalse(flowKey, version).orElseThrow();
        stored.setDeleted(true);
        versions.save(stored);
    }

    @Override
    public FlowVersion save(FlowVersion version) {
        if (version.status() == FlowVersionStatus.ACTIVE) {
            validateStateBounds(version.definition());
        }
        var stored = versions.findByFlowKeyAndVersionAndDeletedFalse(version.flowKey(), version.version())
                .orElseGet(() -> new FlowVersionEntity(version.flowKey(), version.version(),
                        objectMapper.valueToTree(version.definition())));
        stored.setStatus(version.status());
        stored.setDefinition(objectMapper.valueToTree(version.definition()));
        stored = versions.save(stored);
        if (version.status() == FlowVersionStatus.ARCHIVED) {
            // The partial unique index must see the old ACTIVE row leave before the new one enters.
            versions.flush();
        }
        return toVersion(stored);
    }

    private static void validateStateBounds(FlowDefinition definition) {
        validateState(definition.initialState());
        for (var entry : definition.table().getTransitions().entrySet()) {
            validateState(entry.getKey());
            for (var transition : entry.getValue()) {
                validateState(transition.getTo().getState());
            }
        }
    }

    private static void validateState(String state) {
        // orders.state is VARCHAR(120); count code points as the flow loader does.
        if (state.codePointCount(0, state.length()) > 120) {
            throw new IllegalArgumentException("State must contain at most 120 characters");
        }
    }

    private FlowVersion toVersion(FlowVersionEntity stored) {
        return new FlowVersion(stored.getFlowKey(), stored.getVersion(), stored.getStatus(),
                objectMapper.convertValue(stored.getDefinition(), FlowDefinition.class));
    }
}
