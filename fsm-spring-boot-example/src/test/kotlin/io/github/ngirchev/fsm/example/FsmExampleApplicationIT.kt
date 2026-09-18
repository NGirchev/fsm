package io.github.ngirchev.fsm.example

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.ngirchev.fsm.example.flow.FlowDefinition
import io.github.ngirchev.fsm.serialization.FsmDto
import io.github.ngirchev.fsm.serialization.TransitionDto
import io.github.ngirchev.fsm.serialization.ToDto
import io.github.ngirchev.fsm.example.flow.FlowVersion
import io.github.ngirchev.fsm.example.flow.FlowVersionStatus
import io.github.ngirchev.fsm.example.order.CreateOrderRequest
import io.github.ngirchev.fsm.example.order.OrderEventRequest
import io.github.ngirchev.fsm.example.order.OrderResponse
import org.flywaydb.core.Flyway
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
import java.sql.DriverManager
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

        val inProgressOrder = post("/api/orders", CreateOrderRequest(BigDecimal("15.00")), OrderResponse::class.java)
        val inProgressPending = post(
            "/api/orders/${inProgressOrder.id}/events",
            OrderEventRequest("SUBMIT"),
            OrderResponse::class.java,
        )
        assertEquals("PAYMENT_PENDING", inProgressPending.state)

        val draft = post("/api/flows/order/versions", fastDefinition(), FlowVersion::class.java)
        assertEquals(FlowVersionStatus.DRAFT, draft.status)
        val published = post("/api/flows/order/versions/${draft.version}/publish", null, FlowVersion::class.java)
        assertEquals(FlowVersionStatus.ACTIVE, published.status)

        val inProgressPaid = post(
            "/api/orders/${inProgressOrder.id}/events",
            OrderEventRequest("PAY"),
            OrderResponse::class.java,
        )
        assertEquals("PAID", inProgressPaid.state)
        assertEquals(inProgressOrder.flowVersion, inProgressPaid.flowVersion)

        val newOrder = post("/api/orders", CreateOrderRequest(BigDecimal("10.00")), OrderResponse::class.java)
        assertEquals(published.version, newOrder.flowVersion)
        val completed = post("/api/orders/${newOrder.id}/events", OrderEventRequest("FAST_COMPLETE"), OrderResponse::class.java)
        assertEquals("COMPLETED", completed.state)

        val invalidDraft = post(
            "/api/flows/order/versions",
            fastDefinition().copy(
                table = fastDefinition().table.copy(transitions = mapOf(
                    "NEW" to fastDefinition().table.transitions.getValue("NEW").map {
                        it.copy(to = it.to.copy(conditions = listOf("missingBean")))
                    },
                )),
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

    @Test
    fun `migration pins existing orders to the active flow version`() {
        val schema = "migration_upgrade_test"
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .schemas(schema)
            .locations("classpath:db/migration")
            .target("2")
            .load()
            .migrate()
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("INSERT INTO $schema.orders(state, total_amount) VALUES ('NEW', 20.00)")
            }
        }

        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .schemas(schema)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT flow_version FROM $schema.orders").use { result ->
                    assertTrue(result.next())
                    assertEquals(1, result.getInt("flow_version"))
                }
            }
        }
    }

    private fun fastDefinition() = FlowDefinition(
        "NEW",
        FsmDto(false, mapOf("NEW" to listOf(
            TransitionDto("NEW", ToDto("COMPLETED", emptyList(), emptyList(), emptyList(), null), "FAST_COMPLETE"),
        ))),
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
