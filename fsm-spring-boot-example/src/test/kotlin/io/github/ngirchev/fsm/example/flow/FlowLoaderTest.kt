package io.github.ngirchev.fsm.example.flow

import io.github.ngirchev.fsm.Action
import io.github.ngirchev.fsm.Guard
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.IdAction
import io.github.ngirchev.fsm.IdGuard
import io.github.ngirchev.fsm.example.order.Order
import io.github.ngirchev.fsm.example.order.OrderBehaviors
import io.github.ngirchev.fsm.impl.extended.ExDomainFsm
import io.github.ngirchev.fsm.exception.AutoTransitionLimitExceededException
import io.github.ngirchev.fsm.impl.extended.ExFsm
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import io.github.ngirchev.fsm.serialization.FsmDto
import io.github.ngirchev.fsm.serialization.FsmJsonSerializer
import io.github.ngirchev.fsm.serialization.ToDto
import io.github.ngirchev.fsm.serialization.TransitionDto
import io.github.ngirchev.fsm.serialization.TimeoutDto
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlowLoaderTest {
    private var calls = 0
    private val loader = FlowLoader(
        mapOf("mark" to Action<StateContext<String>> { calls++ }),
        mapOf("allowed" to Guard<StateContext<String>> { true }),
    )

    @Test
    fun `Spring injects ordinary core beans and restores guards actions and post actions`() {
        AnnotationConfigApplicationContext(OrderBehaviors::class.java, FlowLoader::class.java).use { context ->
            val to = ToDto("PAID", listOf("paymentApproved"), listOf("capturePayment"), listOf("sendPaymentReceipt"), null)
            val definition = FlowDefinition("PAYMENT_PENDING", FsmDto(false, mapOf(
                "PAYMENT_PENDING" to listOf(TransitionDto("PAYMENT_PENDING", to, "PAY")),
            )))
            val table = context.getBean(FlowLoader::class.java).load(definition)
            val now = OffsetDateTime.parse("2026-09-18T00:00:00Z")
            val order = Order(1, "PAYMENT_PENDING", 1, BigDecimal("42.00"), false, false, 0, now, now)
            ExDomainFsm<Order, String, String>(table).handle(order, "PAY")
            assertEquals("PAID", order.state)
            assertTrue(order.paymentCaptured)
            assertTrue(order.receiptSent)
        }
    }

    @Test
    fun `loads JSON exported by the core and invokes named behaviors`() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("NEW", "GO", "DONE", IdGuard("allowed") { true }, IdAction("mark") { })
            .build()
        val dto = FsmJsonSerializer().deserializeDto(FsmJsonSerializer().serialize(table))
        val restored = loader.load(FlowDefinition("NEW", dto))
        val fsm = ExFsm("NEW", restored)
        fsm.onEvent("GO")
        assertEquals("DONE", fsm.getState())
        assertEquals(1, calls)
        assertEquals(dto, FsmJsonSerializer().deserializeDto(FsmJsonSerializer().serialize(restored)))
    }

    @Test
    fun `unknown guards actions and post actions are never silently discarded`() {
        listOf(
            target().copy(conditions = listOf("missing")),
            target().copy(actions = listOf("missing")),
            target().copy(postActions = listOf("missing")),
        ).forEach { to ->
            assertFailsWith<InvalidFlowDefinitionException> { loader.load(definition(to)) }
        }
        assertEquals(0, calls)
    }

    @Test
    fun `validates PostgreSQL state bounds including terminal and initial states`() {
        listOf("A", "😀").forEach { character ->
            val boundary = character.repeat(120)
            assertTrue(loader.validate(definition(target().copy(state = boundary))).isEmpty())
            assertFailsWith<InvalidFlowDefinitionException> {
                loader.load(definition(target().copy(state = character.repeat(121))))
            }
        }
        assertFailsWith<InvalidFlowDefinitionException> { loader.load(definition().copy(initialState = "")) }
        assertFailsWith<InvalidFlowDefinitionException> { loader.load(definition().copy(initialState = "MISSING")) }
    }

    @Test
    fun `rejects invalid timeouts duplicate transitions and mismatched source keys`() {
        listOf(TimeoutDto(0, "SECONDS"), TimeoutDto(1, "WEEKS")).forEach { timeout ->
            assertFailsWith<InvalidFlowDefinitionException> { loader.load(definition(target().copy(timeout = timeout))) }
        }
        val valid = definition()
        val transition = valid.table.transitions.getValue("NEW").single()
        listOf(listOf(transition, transition), listOf(transition.copy(from = "OTHER"))).forEach { transitions ->
            assertFailsWith<InvalidFlowDefinitionException> {
                loader.load(valid.copy(table = valid.table.copy(transitions = mapOf("NEW" to transitions))))
            }
        }
    }

    @Test
    fun `requires a limit for automatic execution and uses core runtime enforcement`() {
        val table = ExTransitionTable.Builder<String, String>().autoTransitionEnabled(true)
            .maxImmediateAutoTransitions(2)
            .add("NEW", "GO", "RED")
            .add("RED", to = "GREEN")
            .add("GREEN", to = "RED")
            .build()
        val dto = FsmJsonSerializer().deserializeDto(FsmJsonSerializer().serialize(table))
        assertFailsWith<InvalidFlowDefinitionException> {
            loader.load(FlowDefinition("NEW", dto.copy(maxImmediateAutoTransitions = 0)))
        }
        assertFailsWith<AutoTransitionLimitExceededException> {
            ExFsm("NEW", loader.load(FlowDefinition("NEW", dto))).onEvent("GO")
        }
    }

    private fun target() = ToDto("DONE", listOf("allowed"), listOf("mark"), emptyList(), null)
    private fun definition(to: ToDto = target()) = FlowDefinition(
        "NEW", FsmDto(false, mapOf("NEW" to listOf(TransitionDto("NEW", to, "GO")))),
    )
}
