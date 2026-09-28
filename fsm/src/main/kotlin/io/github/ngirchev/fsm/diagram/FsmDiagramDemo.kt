package io.github.ngirchev.fsm.diagram

import io.github.ngirchev.fsm.NamedAction
import io.github.ngirchev.fsm.NamedGuard
import io.github.ngirchev.fsm.Timeout
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import kotlin.io.path.Path

/**
 * FSM diagram generators demonstration
 */
object FsmDiagramDemo {

    enum class OrderState {
        NEW,
        PAYMENT_PENDING,
        PAID,
        PROCESSING,
        SHIPPED,
        DELIVERED,
        CANCELLED,
        REFUNDED
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("=" * 70)
        println("FSM diagram generators demonstration")
        println("=" * 70)
        println()

        // Example 1: Simple order FSM
        simpleOrderFsm()

        // Example 2: Complex FSM with conditions
        complexOrderFsm()
    }

    private fun simpleOrderFsm() {
        println("\n>>> Example 1: Simple order FSM <<<\n")

        val transitionTable = ExTransitionTable.Builder<OrderState, String>()
            .add(from = OrderState.NEW, onEvent = "CREATE_PAYMENT", to = OrderState.PAYMENT_PENDING)
            .add(
                from = OrderState.PAYMENT_PENDING,
                onEvent = "PAYMENT_SUCCESS",
                to = OrderState.PAID,
                action = namedAction("ChargeCard"),
                postAction = namedAction("SendReceipt"),
                timeout = Timeout(5)
            )
            .add(from = OrderState.PAYMENT_PENDING, onEvent = "CANCEL", to = OrderState.CANCELLED)
            .add(
                from = OrderState.PAID,
                onEvent = "START_PROCESSING",
                to = OrderState.PROCESSING,
                action = namedAction("ValidateOrder")
            )
            .add(
                from = OrderState.PROCESSING,
                onEvent = "SHIP",
                to = OrderState.SHIPPED,
                action = namedAction("PreparePackage"),
                postAction = namedAction("NotifyCarrier"),
                timeout = Timeout(2)
            )
            .add(
                from = OrderState.SHIPPED,
                onEvent = "DELIVER",
                to = OrderState.DELIVERED,
                action = namedAction("ConfirmDelivery"),
                postAction = namedAction("SendThankYou"),
                timeout = Timeout(3)
            )
            .add(
                from = OrderState.DELIVERED,
                onEvent = "REFUND",
                to = OrderState.REFUNDED,
                action = namedAction("ProcessRefund"),
                postAction = namedAction("NotifyAccounting")
            )
            .add(from = OrderState.PAID, onEvent = "CANCEL", to = OrderState.CANCELLED)
            .add(from = OrderState.PROCESSING, onEvent = "CANCEL", to = OrderState.CANCELLED)
            .build()

        println("--- PlantUML ---")
        transitionTable.printPlantUml()
        transitionTable.toPlantUml(Path("simple_order_fsm1.plantuml"))

        println("\n--- Mermaid ---")
        transitionTable.printMermaid()
        transitionTable.toMermaid(Path("simple_order_fsm1.mermaid"))
    }

    private fun complexOrderFsm() {
        println("\n>>> Example 2: Complex FSM with conditions and actions <<<\n")

        // Named conditions
        val needsVerification = namedGuard("NeedsVerification", true)
        val isExpressDelivery = namedGuard("IsExpressDelivery", false)
        val isInternational = namedGuard("IsInternational", false)

        // Named actions
        val sendEmail = namedAction("SendEmail")
        val sendSms = namedAction("SendSMS")
        val sendNotification = namedAction("SendNotification")

        val transitionTable = ExTransitionTable.Builder<OrderState, String>()
            .from(OrderState.NEW)
            .onEvent("CREATE_PAYMENT")
            .to(OrderState.PAYMENT_PENDING)
            .action(sendEmail)
            .action(sendSms)
            .end()
            //
            .from(OrderState.PAYMENT_PENDING)
            .onEvent("PAYMENT_SUCCESS")
            .to(OrderState.PAID)
            .onCondition(needsVerification)
            .action(sendNotification)
            .postAction(sendEmail)
            .timeout(Timeout(5))
            .end()
            //
            .from(OrderState.PAID)
            .onEvent("START_PROCESSING")
            .toMultiple()
            .to(OrderState.PROCESSING)
            .onCondition(isExpressDelivery)
            .action(namedAction("ProcessExpress"))
            .timeout(Timeout(1))
            .end()
            .to(OrderState.PROCESSING)
            .onCondition(isInternational)
            .action(namedAction("ProcessInternational"))
            .timeout(Timeout(3))
            .end()
            .to(OrderState.PROCESSING)
            .action(namedAction("ProcessStandard"))
            .timeout(Timeout(2))
            .end()
            .endMultiple()
            //
            .from(OrderState.PROCESSING)
            .onEvent("SHIP")
            .to(OrderState.SHIPPED)
            .action(sendEmail)
            .action(sendSms)
            .postAction(sendNotification)
            .end()
            //
            .from(OrderState.SHIPPED)
            .onEvent("DELIVER")
            .to(OrderState.DELIVERED)
            .action(sendEmail)
            .end()
            //
            .from(OrderState.DELIVERED)
            .onEvent("REFUND")
            .to(OrderState.REFUNDED)
            .end()
            //
            .from(OrderState.PAYMENT_PENDING)
            .onEvent("CANCEL")
            .to(OrderState.CANCELLED)
            .end()
            //
            .from(OrderState.PAID)
            .onEvent("CANCEL")
            .to(OrderState.CANCELLED)
            .end()
            //
            .from(OrderState.PROCESSING)
            .onEvent("CANCEL")
            .to(OrderState.CANCELLED)
            .end()
            //
            .build()

        println("--- PlantUML ---")
        transitionTable.printPlantUml()
        transitionTable.toPlantUml(Path("simple_order_fsm2.plantuml"))

        println("\n--- Mermaid ---")
        transitionTable.printMermaid()
        transitionTable.toMermaid(Path("simple_order_fsm2.mermaid"))
    }

    private fun namedAction(name: String): NamedAction<Any> =
        NamedAction<Any>(name) { }.also { it(Any()) }

    private fun namedGuard(name: String, result: Boolean): NamedGuard<Any> =
        NamedGuard<Any>(name) { result }.also { it(Any()) }

    private operator fun String.times(n: Int): String = this.repeat(n)
}
