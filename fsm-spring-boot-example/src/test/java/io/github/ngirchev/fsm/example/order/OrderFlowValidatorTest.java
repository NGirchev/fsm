package io.github.ngirchev.fsm.example.order;

import io.github.ngirchev.fsm.Action;
import io.github.ngirchev.fsm.Guard;
import io.github.ngirchev.fsm.StateChangeListener;
import io.github.ngirchev.fsm.serialization.FsmDto;
import io.github.ngirchev.fsm.serialization.ToDto;
import io.github.ngirchev.fsm.serialization.TransitionDto;
import io.github.ngirchev.fsm.spring.definition.FlowDefinition;
import io.github.ngirchev.fsm.spring.definition.FlowExecution;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderFlowValidatorTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Beans.class);

    @Test
    void acceptsOrderBeansAndRejectsOtherContextBeansInEveryTransitionSlot() {
        runner.run(context -> {
            var validator = context.getBean(OrderFlowValidator.class);
            assertThatCode(() -> validator.validate(definition("orderGuard", "orderAction", "orderAction")))
                    .doesNotThrowAnyException();
            for (var invalid : List.of(
                    definition("foreignGuard", "orderAction", "orderAction"),
                    definition("orderGuard", "foreignAction", "orderAction"),
                    definition("orderGuard", "orderAction", "foreignAction"))) {
                assertThatThrownBy(() -> validator.validate(invalid))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("foreign");
            }
        });
    }

    @Test
    void rejectsMissingOrDuplicateListeners() {
        runner.run(context -> {
            var validator = context.getBean(OrderFlowValidator.class);
            for (var execution : List.of(
                    new FlowExecution(List.of("missing"), List.of()),
                    new FlowExecution(List.of(), List.of("missing")),
                    new FlowExecution(List.of("listener", "listener"), List.of()),
                    new FlowExecution(List.of(), List.of("completion", "completion")))) {
                var invalid = new FlowDefinition("NEW", new FsmDto(false, Map.of("NEW", List.of())), null, execution);
                assertThatThrownBy(() -> validator.validate(invalid)).isInstanceOf(IllegalArgumentException.class);
            }
        });
    }

    private static FlowDefinition definition(String guard, String action, String postAction) {
        var to = new ToDto("DONE", List.of(guard), List.of(action), List.of(postAction), null);
        var table = new FsmDto(false, Map.of("NEW", List.of(new TransitionDto("NEW", to, "GO")), "DONE", List.of()));
        return new FlowDefinition("NEW", table, null, new FlowExecution(List.of("listener"), List.of("completion")));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(OrderFlowValidator.class)
    static class Beans {
        @Bean Guard<Order> orderGuard() { return order -> true; }
        @Bean Guard<String> foreignGuard() { return text -> true; }
        @Bean Action<Order> orderAction() { return order -> {}; }
        @Bean Action<String> foreignAction() { return text -> {}; }
        @Bean StateChangeListener<String> listener() { return (context, from, to) -> {}; }
        @Bean Consumer<Order> completion() { return order -> {}; }
    }
}
