package io.github.ngirchev.fsm.example.order;

import io.github.ngirchev.fsm.spring.definition.FlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class OrderService {
    // Key of the order definition inserted by the Flyway seed.
    private static final String ORDER_FLOW = "order";
    // Loads and locks managed JPA orders; Hibernate saves their FSM changes on commit.
    private final OrderRepository orders;
    // Resolves the active definition for new orders or the pinned version for existing ones.
    private final FlowService flows;
    // Restores the executable table from the selected definition.
    private final OrderExecution execution;
    private final OrderHistoryRepository history;

    // POST /api/orders: create the order and execute initial automatic transitions in one transaction.
    @Transactional
    public Order create(java.math.BigDecimal amount) {
        var activeFlow = flows.active(ORDER_FLOW);
        var order = orders.saveAndFlush(new Order(activeFlow.definition().initialState(), activeFlow.version(), amount));
        execution.run(order, activeFlow.definition(), null);
        return order;
    }

    public Order get(long id) {
        return orders.findById(id).orElseThrow(() -> new NoSuchElementException("Order " + id + " was not found"));
    }

    // POST /api/orders/{id}/events: lock the order and execute its event in one transaction.
    @Transactional
    public Order handle(long id, String event) {
        return handle(id, new OrderEvent(event, null, null));
    }

    @Transactional
    public Order handle(long id, OrderEvent event) {
        var order = orders.findByIdForUpdate(id).orElseThrow(() -> new NoSuchElementException("Order " + id + " was not found"));
        var orderFlow = flows.get(ORDER_FLOW, order.getFlowVersion());
        execution.run(order, orderFlow.definition(), event);
        return order;
    }

    public java.util.List<OrderHistory> history(long id) {
        get(id);
        return history.findByOrderIdOrderById(id);
    }
}
