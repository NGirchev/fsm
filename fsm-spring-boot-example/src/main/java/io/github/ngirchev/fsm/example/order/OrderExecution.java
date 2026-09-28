package io.github.ngirchev.fsm.example.order;

import io.github.ngirchev.fsm.StateChangeListener;
import io.github.ngirchev.fsm.impl.extended.ExFsm;
import io.github.ngirchev.fsm.impl.extended.ExTransition;
import io.github.ngirchev.fsm.spring.FsmBeanRegistry;
import io.github.ngirchev.fsm.spring.definition.FlowDefinition;
import io.github.ngirchev.fsm.spring.definition.FlowLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.function.Consumer;
import kotlin.Unit;

@Component
@RequiredArgsConstructor
public class OrderExecution {
    private final FlowLoader loader;
    private final Map<String, StateChangeListener<String>> stateListeners;
    private final Map<String, Consumer<Order>> completionListeners;
    private final FsmBeanRegistry registry;
    private final OrderBehaviorCatalog behaviorCatalog;

    public void run(Order order, FlowDefinition definition, OrderEvent event) {
        behaviorCatalog.validate(definition);
        order.setEvent(event);
        order.getTrace().clear();
        var table = loader.load(definition, name -> new OrderEvent(name, null, null));
        var fsm = new ExFsm<>(order, table, table.getAutoTransitionEnabled()) {
            @Override
            protected void transitionExecution(@org.springframework.lang.NonNull ExTransition<String, OrderEvent> transition) {
                var before = OrderTrace.Snapshot.of(order);
                super.transitionExecution(transition);
                var destination = transition.getTo();
                order.getTrace().add(new OrderTrace(transition.getFrom(), destination.getState(),
                        transition.getEvent() == null ? null : transition.getEvent().eventType(),
                        destination.getConditions().stream().map(registry::guardName).toList(),
                        destination.getActions().stream().map(registry::actionName).toList(),
                        destination.getPostActions().stream().map(registry::actionName).toList(),
                        before, OrderTrace.Snapshot.of(order)));
            }
        };
        execute(fsm, order, definition, event);
    }

    private <E> void execute(ExFsm<String, E> fsm, Order order, FlowDefinition definition, E event) {
        for (String name : definition.execution().stateListeners()) {
            fsm.addStateChangeListener(stateListeners.get(name));
        }
        for (String name : definition.execution().completionListeners()) {
            fsm.addAutoTransitionCompletionListener(() -> {
                completionListeners.get(name).accept(order);
                return Unit.INSTANCE;
            });
        }
        if (event == null) fsm.startAutoTransitions();
        else fsm.onEvent(event);
    }
}
