package io.github.ngirchev.fsm.example

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.ngirchev.fsm.example.flow.FlowDefinition
import io.github.ngirchev.fsm.example.flow.FlowTransitionDefinition
import io.github.ngirchev.fsm.example.flow.FlowTriggerDefinition
import io.github.ngirchev.fsm.example.flow.FlowVersion
import io.github.ngirchev.fsm.example.flow.FlowVersionStatus
import io.github.ngirchev.fsm.example.order.CreateOrderRequest
import io.github.ngirchev.fsm.example.order.OrderEventRequest
import io.github.ngirchev.fsm.example.order.OrderResponse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FsmExampleApplicationIT {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var rest: TestRestTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `runs seeded flow and applies a newly published version without restart`() {
        val order = post("/api/orders", CreateOrderRequest(BigDecimal("42.00")), OrderResponse::class.java)
        val pending = post("/api/orders/${order.id}/events", OrderEventRequest("SUBMIT"), OrderResponse::class.java)
        val paid = post("/api/orders/${order.id}/events", OrderEventRequest("PAY"), OrderResponse::class.java)
        assertEquals("PAYMENT_PENDING", pending.state)
        assertEquals("PAID", paid.state)
        assertTrue(paid.paymentCaptured)
        assertTrue(paid.receiptSent)

        val draft = post("/api/flows/order/versions", fastDefinition(), FlowVersion::class.java)
        assertEquals(FlowVersionStatus.DRAFT, draft.status)
        val published = post("/api/flows/order/versions/${draft.version}/publish", null, FlowVersion::class.java)
        assertEquals(FlowVersionStatus.ACTIVE, published.status)

        val newOrder = post("/api/orders", CreateOrderRequest(BigDecimal("10.00")), OrderResponse::class.java)
        val completed = post("/api/orders/${newOrder.id}/events", OrderEventRequest("FAST_COMPLETE"), OrderResponse::class.java)
        assertEquals("COMPLETED", completed.state)

        val invalidDraft = post(
            "/api/flows/order/versions",
            fastDefinition().copy(
                transitions = fastDefinition().transitions.map { it.copy(guards = listOf("missingBean")) },
            ),
            FlowVersion::class.java,
        )
        val invalidPublish = rest.postForEntity(
            url("/api/flows/order/versions/${invalidDraft.version}/publish"),
            null,
            ApiError::class.java,
        )
        assertEquals(HttpStatus.BAD_REQUEST, invalidPublish.statusCode)
        assertFalse(invalidPublish.body!!.issues.isEmpty())

        val stillWorks = post("/api/orders", CreateOrderRequest(BigDecimal("11.00")), OrderResponse::class.java)
        val stillCompleted = post("/api/orders/${stillWorks.id}/events", OrderEventRequest("FAST_COMPLETE"), OrderResponse::class.java)
        assertEquals("COMPLETED", stillCompleted.state)
    }

    @Test
    fun `active version cannot be overwritten`() {
        val response = rest.exchange(
            url("/api/flows/order/versions/1"),
            HttpMethod.PUT,
            HttpEntity(fastDefinition()),
            ApiError::class.java,
        )
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    private fun fastDefinition() = FlowDefinition(
        schemaVersion = 1,
        initialState = "NEW",
        states = listOf("NEW", "COMPLETED"),
        events = listOf("FAST_COMPLETE"),
        transitions = listOf(
            FlowTransitionDefinition(
                id = "fast-complete",
                from = "NEW",
                to = "COMPLETED",
                trigger = FlowTriggerDefinition("event", "FAST_COMPLETE"),
            ),
        ),
    )

    private fun <T> post(path: String, body: Any?, responseType: Class<T>): T {
        val response = rest.postForEntity(url(path), body, String::class.java)
        assertTrue(
            response.statusCode.is2xxSuccessful,
            "Unexpected response: ${response.statusCode} ${response.body}",
        )
        return objectMapper.readValue(requireNotNull(response.body), responseType)
    }

    private fun url(path: String) = "http://localhost:$port$path"

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
