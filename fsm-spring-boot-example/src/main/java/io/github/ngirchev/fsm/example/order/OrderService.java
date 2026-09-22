package io.github.ngirchev.fsm.example.order;

import io.github.ngirchev.fsm.spring.definition.FlowLoader;
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
    private final FlowLoader loader;

    // POST /api/orders: create the order and execute initial automatic transitions in one transaction.
    @Transactional
    public Order create() {
        return create(true);
    }

    @Transactional
    public Order create(boolean approved) {
        var activeFlow = flows.active(ORDER_FLOW);
        var order = orders.saveAndFlush(new Order(activeFlow.definition().initialState(), activeFlow.version(), approved));
        loader.load(activeFlow.definition()).<Order>createDomainFsm().getFsmForDomain(order).startAutoTransitions();
        return order;
    }

    public Order get(long id) {
        return orders.findById(id).orElseThrow(() -> new NoSuchElementException("Order " + id + " was not found"));
    }

    // POST /api/orders/{id}/events: lock the order and execute its event in one transaction.
    @Transactional
    public Order handle(long id, String event) {
        var order = orders.findByIdForUpdate(id).orElseThrow(() -> new NoSuchElementException("Order " + id + " was not found"));
        var orderFlow = flows.get(ORDER_FLOW, order.getFlowVersion());
        loader.load(orderFlow.definition()).<Order>createDomainFsm().handle(order, event);
        return order;
    }
}
