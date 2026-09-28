package io.github.ngirchev.fsm.example.order;

import io.github.ngirchev.fsm.Action;
import io.github.ngirchev.fsm.Guard;
import io.github.ngirchev.fsm.StateChangeListener;
import io.github.ngirchev.fsm.spring.definition.FlowDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class OrderFlowValidator {
    private final Map<String, Guard<Order>> guards;
    private final Map<String, Action<Order>> actions;
    private final Map<String, StateChangeListener<String>> stateListeners;
    private final Map<String, Consumer<Order>> completionListeners;

    public void validate(FlowDefinition definition) {
        OrderEvent.validateFlowEvents(definition);
        definition.table().getTransitions().values().forEach(transitions -> transitions.forEach(transition -> {
            var to = transition.getTo();
            validateNames(to.getConditions(), guards, "guard");
            validateNames(to.getActions(), actions, "action");
            validateNames(to.getPostActions(), actions, "action");
        }));
        var execution = definition.execution();
        validateListeners(execution.stateListeners(), stateListeners, "stateListener");
        validateListeners(execution.completionListeners(), completionListeners, "completionListener");
    }

    private static void validateListeners(List<String> names, Map<String, ?> beans, String kind) {
        if (names.stream().distinct().count() != names.size()) {
            throw new IllegalArgumentException("Duplicate " + kind);
        }
        validateNames(names, beans, kind);
    }

    private static void validateNames(List<String> names, Map<String, ?> beans, String kind) {
        for (String name : names) {
            if (!beans.containsKey(name)) {
                throw new IllegalArgumentException("Unknown " + kind + " bean: " + name);
            }
        }
    }
}
