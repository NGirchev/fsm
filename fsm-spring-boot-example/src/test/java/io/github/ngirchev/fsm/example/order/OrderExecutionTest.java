package io.github.ngirchev.fsm.example.order;

import io.github.ngirchev.fsm.Action;
import io.github.ngirchev.fsm.Guard;
import io.github.ngirchev.fsm.StateContext;
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable;
import io.github.ngirchev.fsm.serialization.FsmDto;
import io.github.ngirchev.fsm.spring.FsmBeanRegistry;
import io.github.ngirchev.fsm.spring.definition.FlowDefinition;
import io.github.ngirchev.fsm.spring.definition.FlowLoader;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderExecutionTest {
    @Test
    void amountAbove10000UsesStrictBoundary() {
        var guard = new OrderBehaviorConfiguration().amountAbove10000();
        assertThat(guard.invoke(new Order("NEW", 1, new BigDecimal("10000.00")))).isFalse();
        assertThat(guard.invoke(new Order("NEW", 1, new BigDecimal("10000.01")))).isTrue();
    }

    @Test
    void tracesActualActionsAndAutomaticStepsWithIndependentSnapshots() {
        Guard<StateContext<String>> belowThreshold = context ->
                ((Order) context).getAmount().compareTo(new BigDecimal("1000.00")) < 0;
        Action<StateContext<String>> commission = context -> ((Order) context).setCommission(new BigDecimal("2.00"));
        Action<StateContext<String>> notify = context -> { };
        var registry = new FsmBeanRegistry(Map.of("commissionTwoPercent", commission, "logOrderNotification", notify),
                Map.of("amountBelowCommissionThreshold", belowThreshold));
        var loader = mock(FlowLoader.class);
        var definition = new FlowDefinition("NEW", new FsmDto(false, Map.of()));
        var table = new ExTransitionTable.Builder<String, OrderEvent>()
                .autoTransitionEnabled(true).maxImmediateAutoTransitions(2)
                .add("NEW", new OrderEvent("GO", null, null), "READY", belowThreshold, commission, null, null)
                .add("READY", null, "DONE", null, null, notify, null).build();
        doReturn(table).when(loader).load(eq(definition), any());
        var execution = new OrderExecution(loader, Map.of(), Map.of(), registry, mock(OrderFlowValidator.class));
        var order = new Order("NEW", 1, new BigDecimal("100.00"));
        execution.run(order, definition, new OrderEvent("GO", null, null));
        assertThat(order.getTrace()).hasSize(2);
        var first = order.getTrace().get(0);
        assertThat(first.event()).isEqualTo("GO");
        assertThat(first.conditions()).containsExactly("amountBelowCommissionThreshold");
        assertThat(first.actions()).containsExactly("commissionTwoPercent");
        assertThat(first.before().commission()).isEqualByComparingTo("0");
        assertThat(first.after().commission()).isEqualByComparingTo("2");
        var second = order.getTrace().get(1);
        assertThat(second.event()).isNull();
        assertThat(second.postActions()).containsExactly("logOrderNotification");
        assertThatThrownBy(() -> execution.run(order, definition, new OrderEvent("GO", null, null)))
                .isInstanceOf(io.github.ngirchev.fsm.exception.FsmException.class);
        assertThat(order.getTrace()).isEmpty();
    }
}
