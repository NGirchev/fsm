package io.github.ngirchev.fsm.example.order;

import io.github.ngirchev.fsm.Action;
import io.github.ngirchev.fsm.Guard;
import io.github.ngirchev.fsm.StateChangeListener;
import io.github.ngirchev.fsm.spring.definition.FlowDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@RestController
@RequiredArgsConstructor
public class OrderBehaviorCatalog {
    public record Behavior(String id, String kind, String description) {}

    private final Map<String, Guard<Order>> guards;
    private final Map<String, Action<Order>> actions;
    private final Map<String, StateChangeListener<String>> stateListeners;
    private final Map<String, Consumer<Order>> completionListeners;
    private final ConfigurableListableBeanFactory beanFactory;

    @GetMapping("/api/flows/order/behaviors")
    public List<Behavior> list() {
        var behaviors = new ArrayList<Behavior>();
        add(behaviors, guards, "guard");
        add(behaviors, actions, "action");
        add(behaviors, stateListeners, "stateListener");
        add(behaviors, completionListeners, "completionListener");
        return behaviors;
    }

    private void add(List<Behavior> behaviors, Map<String, ?> beans, String kind) {
        for (String name : beans.keySet()) {
            String description = beanFactory.getBeanDefinition(name).getDescription();
            behaviors.add(new Behavior(name, kind, description == null ? name : description));
        }
    }

    public void validate(FlowDefinition definition) {
        OrderEvent.validateFlowEvents(definition);
        var execution = definition.execution();
        validateNames(execution.stateListeners(), stateListeners, "stateListener");
        validateNames(execution.completionListeners(), completionListeners, "completionListener");
    }

    private static void validateNames(List<String> names, Map<String, ?> beans, String kind) {
        if (names.stream().distinct().count() != names.size()) {
            throw new IllegalArgumentException("Duplicate " + kind);
        }
        for (String name : names) {
            if (!beans.containsKey(name)) {
                throw new IllegalArgumentException("Unknown " + kind + " bean: " + name);
            }
        }
    }
}
