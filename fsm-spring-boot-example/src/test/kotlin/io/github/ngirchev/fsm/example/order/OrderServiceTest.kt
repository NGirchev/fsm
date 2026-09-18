package io.github.ngirchev.fsm.example.order

import io.github.ngirchev.fsm.example.ApiExceptionHandler
import io.github.ngirchev.fsm.example.flow.ActiveFlow
import io.github.ngirchev.fsm.example.flow.ActiveFlowProvider
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrderServiceTest {
    private val orders = mock(OrderRepository::class.java)
    private val flows = mock(ActiveFlowProvider::class.java)
    private val service = OrderService(orders, flows)

    @Test
    fun `creation executes initial automatic actions and saves the resulting order`() {
        val table = ExTransitionTable.Builder<String, String>()
            .autoTransitionEnabled(true)
            .maxImmediateAutoTransitions(1)
            .add("NEW", to = "PAID", action = { (it as Order).paymentCaptured = true },
                postAction = { (it as Order).receiptSent = true })
            .build()
        val request = CreateOrderRequest(BigDecimal("42.00"))
        val now = OffsetDateTime.parse("2026-09-18T00:00:00Z")
        val order = Order(1, "NEW", 2, request.totalAmount, false, false, 0, now, now)
        `when`(flows.get("order")).thenReturn(ActiveFlow(2, "NEW", table))
        `when`(orders.create("NEW", 2, request)).thenReturn(order)
        `when`(orders.save(order)).thenAnswer { order.copy(lockVersion = 1) }

        val created = service.create(request)

        assertEquals("PAID", created.state)
        assertEquals(1L, created.lockVersion)
        assertTrue(created.paymentCaptured)
        assertTrue(created.receiptSent)
        verify(orders).save(order)
    }

    @Test
    fun `rejects amounts requiring rounding or exceeding numeric precision before persistence`() {
        listOf("0.001", "42.001", "100000000000000000.00", "0", "-1.00").forEach { amount ->
            assertFailsWith<IllegalArgumentException> { service.create(CreateOrderRequest(BigDecimal(amount))) }
        }
        verifyNoInteractions(orders, flows)
    }

    @Test
    fun `accepts exactly representable amounts including trailing zeroes`() {
        `when`(flows.get("order")).thenReturn(ActiveFlow(1, "NEW", ExTransitionTable.Builder<String, String>().build()))
        listOf("0.01", "42.0000", "99999999999999999.9900", "1E+16").forEach { amount ->
            val request = CreateOrderRequest(BigDecimal(amount))
            val now = OffsetDateTime.parse("2026-09-18T00:00:00Z")
            val order = Order(1, "NEW", 1, request.totalAmount, false, false, 0, now, now)
            `when`(orders.create("NEW", 1, request)).thenReturn(order)

            assertEquals(order, service.create(request))
            verify(orders).create("NEW", 1, request)
        }
    }

    @Test
    fun `invalid amounts return bad request through the order API`() {
        val mvc = MockMvcBuilders.standaloneSetup(OrderController(service))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
        listOf("0.001", "100000000000000000.00").forEach { amount ->
            mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{\"totalAmount\":$amount}"))
                .andExpect(status().isBadRequest)
        }
        verifyNoInteractions(orders, flows)
    }
}
