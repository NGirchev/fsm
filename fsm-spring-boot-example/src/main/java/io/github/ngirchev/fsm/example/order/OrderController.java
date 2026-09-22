package io.github.ngirchev.fsm.example.order;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    // HTTP requests enter here; the service owns transactions and invokes the FSM.
    private final OrderService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@RequestBody(required = false) CreateOrderRequest request) {
        return service.create(request == null || request.approved() == null || request.approved()).toResponse();
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable long id) {
        return service.get(id).toResponse();
    }

    @PostMapping("/{id}/events")
    public OrderResponse handle(@PathVariable long id, @RequestBody OrderEventRequest request) {
        return service.handle(id, request.event()).toResponse();
    }

    public record OrderEventRequest(
            // POST /api/orders/{id}/events supplies an event from the order's pinned definition.
            String event
    ) {
        public OrderEventRequest {
            Objects.requireNonNull(event, "event");
        }
    }

    // HTTP snapshot created by Order.toResponse(); excludes the transient FSM transition.
    public record CreateOrderRequest(Boolean approved) {
    }

    public record OrderResponse(long id, String state, int flowVersion, boolean approved, boolean notificationSent) {
    }
}
