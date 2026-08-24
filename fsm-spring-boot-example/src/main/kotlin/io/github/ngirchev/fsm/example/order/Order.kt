package io.github.ngirchev.fsm.example.order

import com.fasterxml.jackson.annotation.JsonIgnore
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.Transition
import java.math.BigDecimal
import java.time.OffsetDateTime

data class Order(
    val id: Long,
    override var state: String,
    val totalAmount: BigDecimal,
    var paymentCaptured: Boolean,
    var receiptSent: Boolean,
    val lockVersion: Long,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    @get:JsonIgnore override var currentTransition: Transition<String>? = null,
) : StateContext<String>

data class CreateOrderRequest(val totalAmount: BigDecimal)
data class OrderEventRequest(val event: String)

data class OrderResponse(
    val id: Long,
    val state: String,
    val totalAmount: BigDecimal,
    val paymentCaptured: Boolean,
    val receiptSent: Boolean,
    val lockVersion: Long,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

fun Order.toResponse() = OrderResponse(
    id = id,
    state = state,
    totalAmount = totalAmount,
    paymentCaptured = paymentCaptured,
    receiptSent = receiptSent,
    lockVersion = lockVersion,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
