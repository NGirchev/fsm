package io.github.ngirchev.fsm.example.order

import io.github.ngirchev.fsm.example.flow.ActiveFlowProvider
import io.github.ngirchev.fsm.impl.extended.ExDomainFsm
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderService(
    private val orders: OrderRepository,
    private val flows: ActiveFlowProvider,
) {
    @Transactional
    fun create(request: CreateOrderRequest): Order {
        require(request.totalAmount.signum() > 0) { "totalAmount must be positive" }
        val activeFlow = flows.get(ORDER_FLOW)
        return orders.create(activeFlow.initialState, activeFlow.version, request)
    }

    fun get(id: Long): Order = orders.get(id)

    @Transactional
    fun handle(id: Long, event: String): Order {
        require(event.isNotBlank()) { "event must not be blank" }
        val order = orders.getForUpdate(id)
        val orderFlow = flows.get(ORDER_FLOW, order.flowVersion)
        ExDomainFsm<Order, String, String>(orderFlow.transitionTable).handle(order, event)
        return orders.save(order)
    }

    companion object {
        private const val ORDER_FLOW = "order"
    }
}
