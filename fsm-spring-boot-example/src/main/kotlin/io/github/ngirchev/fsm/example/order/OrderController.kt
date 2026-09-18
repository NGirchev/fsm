package io.github.ngirchev.fsm.example.order

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/orders")
class OrderController(private val service: OrderService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateOrderRequest): OrderResponse = service.create(request).toResponse()

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): OrderResponse = service.get(id).toResponse()

    @PostMapping("/{id}/events")
    fun handle(@PathVariable id: Long, @RequestBody request: OrderEventRequest): OrderResponse =
        service.handle(id, request.event).toResponse()
}
