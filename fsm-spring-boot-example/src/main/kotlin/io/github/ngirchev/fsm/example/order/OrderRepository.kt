package io.github.ngirchev.fsm.example.order

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime

class OrderNotFoundException(id: Long) : NoSuchElementException("Order $id was not found")
class ConcurrentOrderUpdateException(id: Long) : IllegalStateException("Order $id was updated concurrently")

@Repository
class OrderRepository(private val jdbc: JdbcClient) {
    fun create(initialState: String, request: CreateOrderRequest): Order {
        val id = jdbc.sql(
            "INSERT INTO orders(state, total_amount) VALUES (:state, :amount) RETURNING id",
        )
            .param("state", initialState)
            .param("amount", request.totalAmount)
            .query(Long::class.java)
            .single()
        return get(id)
    }

    fun get(id: Long): Order = jdbc.sql(SELECT + " WHERE id = :id")
        .param("id", id)
        .query(::mapOrder)
        .optional()
        .orElseThrow { OrderNotFoundException(id) }

    fun getForUpdate(id: Long): Order = jdbc.sql(SELECT + " WHERE id = :id FOR UPDATE")
        .param("id", id)
        .query(::mapOrder)
        .optional()
        .orElseThrow { OrderNotFoundException(id) }

    fun save(order: Order): Order {
        val changed = jdbc.sql(
            """
            UPDATE orders
            SET state = :state, payment_captured = :paymentCaptured, receipt_sent = :receiptSent,
                lock_version = lock_version + 1, updated_at = now()
            WHERE id = :id AND lock_version = :lockVersion
            """.trimIndent(),
        )
            .param("state", order.state)
            .param("paymentCaptured", order.paymentCaptured)
            .param("receiptSent", order.receiptSent)
            .param("id", order.id)
            .param("lockVersion", order.lockVersion)
            .update()
        if (changed != 1) throw ConcurrentOrderUpdateException(order.id)
        return get(order.id)
    }

    private fun mapOrder(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): Order = Order(
        id = rs.getLong("id"),
        state = rs.getString("state"),
        totalAmount = rs.getBigDecimal("total_amount"),
        paymentCaptured = rs.getBoolean("payment_captured"),
        receiptSent = rs.getBoolean("receipt_sent"),
        lockVersion = rs.getLong("lock_version"),
        createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
        updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
    )

    companion object {
        private const val SELECT = """
            SELECT id, state, total_amount, payment_captured, receipt_sent,
                   lock_version, created_at, updated_at
            FROM orders
        """
    }
}
