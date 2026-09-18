package io.github.ngirchev.fsm.impl.extended;

import io.github.ngirchev.fsm.AutoTransitionScheduler;
import io.github.ngirchev.fsm.StateContext;
import io.github.ngirchev.fsm.To;
import io.github.ngirchev.fsm.Transition;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaDomainFsmExtensibilityTest {

    @Test
    void javaCanUseToConstructorWithoutScheduler() {
        To<OrderState> to = new To<>(
            OrderState.SUBMITTED,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            null
        );

        assertEquals(OrderState.SUBMITTED, to.getState());
    }

    @Test
    void javaDomainFsmCanHideGenericTypesAndOverrideEventMatching() {
        OrderFsm fsm = OrderFsm.create();
        Order order = new Order(OrderState.NEW);

        fsm.submit(order, "tenant-a", "runtime-request");

        assertEquals(OrderState.SUBMITTED, order.getState());
    }

    private static final class OrderFsm extends ExDomainFsm<Order, OrderState, OrderEvent> {

        private OrderFsm(
            ExTransitionTable<OrderState, OrderEvent> transitionTable,
            boolean autoTransitionEnabled,
            AutoTransitionScheduler<OrderState> autoTransitionScheduler
        ) {
            super(transitionTable, autoTransitionEnabled, autoTransitionScheduler);
        }

        static OrderFsm create() {
            OrderTransitionTable table = new ExTransitionTable.Builder<OrderState, OrderEvent>()
                .add(new ExTransition<>(
                    OrderState.NEW,
                    OrderState.SUBMITTED,
                    new OrderEvent(OrderEventType.SUBMIT, "tenant-a", "definition-request")
                ))
                .add(new ExTransition<>(
                    OrderState.NEW,
                    OrderState.REJECTED,
                    new OrderEvent(OrderEventType.SUBMIT, "tenant-b", "definition-request")
                ))
                .build(OrderTransitionTable::new);

            return table.<Order, OrderFsm>createDomainFsm(OrderFsm::new);
        }

        void submit(Order order, String tenantId, String requestId) {
            handle(order, new OrderEvent(OrderEventType.SUBMIT, tenantId, requestId));
        }
    }

    private static final class OrderTransitionTable extends ExTransitionTable<OrderState, OrderEvent> {

        private OrderTransitionTable(
            Map<OrderState, LinkedHashSet<ExTransition<OrderState, OrderEvent>>> transitions,
            boolean autoTransitionEnabled,
            AutoTransitionScheduler<OrderState> autoTransitionScheduler
        ) {
            super(transitions, autoTransitionEnabled, autoTransitionScheduler);
        }

        @Override
        protected Object eventIdentity(OrderEvent event) {
            return event == null ? null : new OrderEventIdentity(event.type, event.tenantId);
        }
    }

    private static final class Order implements StateContext<OrderState> {

        private OrderState state;
        private Transition<OrderState> currentTransition;

        private Order(OrderState state) {
            this.state = state;
        }

        @Override
        public OrderState getState() {
            return state;
        }

        @Override
        public void setState(OrderState state) {
            this.state = state;
        }

        @Override
        public Transition<OrderState> getCurrentTransition() {
            return currentTransition;
        }

        @Override
        public void setCurrentTransition(Transition<OrderState> currentTransition) {
            this.currentTransition = currentTransition;
        }
    }

    private enum OrderState {
        NEW,
        SUBMITTED,
        REJECTED
    }

    private enum OrderEventType {
        SUBMIT
    }

    private static final class OrderEvent {

        private final OrderEventType type;
        private final String tenantId;
        private final String requestId;

        private OrderEvent(OrderEventType type, String tenantId, String requestId) {
            this.type = type;
            this.tenantId = tenantId;
            this.requestId = requestId;
        }

        @Override
        public boolean equals(Object other) {
            throw new AssertionError("OrderEvent.equals must not be used for transition matching");
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, tenantId, requestId);
        }

        @Override
        public String toString() {
            return "OrderEvent{" +
                "type=" + type +
                ", tenantId='" + tenantId + '\'' +
                ", requestId='" + requestId + '\'' +
                '}';
        }
    }

    private static final class OrderEventIdentity {

        private final OrderEventType type;
        private final String tenantId;

        private OrderEventIdentity(OrderEventType type, String tenantId) {
            this.type = type;
            this.tenantId = tenantId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof OrderEventIdentity)) {
                return false;
            }
            OrderEventIdentity that = (OrderEventIdentity) other;
            return type == that.type && Objects.equals(tenantId, that.tenantId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, tenantId);
        }
    }
}
