package io.github.ngirchev.fsm.example.order;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    // HTTP requests enter here; the service owns transactions and invokes the FSM.
    private final OrderService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@RequestBody CreateOrderRequest request) {
        if (request.amount() == null) {
            throw new IllegalArgumentException("amount is required");
        }
        return service.create(request.amount()).toResponse();
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable long id) {
        return service.get(id).toResponse();
    }

    @PostMapping("/{id}/events")
    public OrderResponse handle(@PathVariable long id, @RequestBody OrderEventRequest request) {
        return service.handle(id, new OrderEvent(request.event(), request.source(), request.requestId())).toResponse();
    }

    @GetMapping("/{id}/history")
    public List<OrderHistory> history(@PathVariable long id) { return service.history(id); }

    public record OrderEventRequest(
            // POST /api/orders/{id}/events supplies an event from the order's pinned definition.
            String event, String source, String requestId
    ) {
        public OrderEventRequest {
            new OrderEvent(event, source, requestId);
        }
        public OrderEventRequest(String event) { this(event, null, null); }
    }

    // HTTP snapshot created by Order.toResponse(); excludes the transient FSM transition.
    public record CreateOrderRequest(BigDecimal amount) {}

    public record OrderResponse(long id, String state, int flowVersion,
                                BigDecimal amount, BigDecimal commission,
                                List<OrderTrace> trace) {
    }
}
