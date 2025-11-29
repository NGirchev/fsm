package ru.girchev.fsm.diagram

import org.junit.jupiter.api.Test
import ru.girchev.fsm.NamedAction
import ru.girchev.fsm.NamedGuard
import ru.girchev.fsm.Timeout
import ru.girchev.fsm.impl.extended.ExTransitionTable

class NamedActionsExampleTest {

    enum class PaymentState {
        NEW,
        PENDING_VERIFICATION,
        VERIFIED,
        PROCESSING,
        COMPLETED,
        FAILED,
        REFUNDED
    }

    @Test
    fun `should generate diagram with named actions and conditions`() {
        // Named conditions
        val isHighRisk = NamedGuard<Any>("IsHighRisk") { false }
        val isLowAmount = NamedGuard<Any>("IsLowAmount") { true }
        val isFraudDetected = NamedGuard<Any>("IsFraudDetected") { false }
        val isVerified = NamedGuard<Any>("IsVerified") { true }

        // Named actions
        val sendEmail = NamedAction<Any>("SendEmail") { }
        val sendSms = NamedAction<Any>("SendSMS") { }
        val logTransaction = NamedAction<Any>("LogTransaction") { }
        val updateDatabase = NamedAction<Any>("UpdateDatabase") { }
        val notifyUser = NamedAction<Any>("NotifyUser") { }
        val chargeCard = NamedAction<Any>("ChargeCard") { }
        val releaseHold = NamedAction<Any>("ReleaseHold") { }
        val createAuditRecord = NamedAction<Any>("CreateAuditRecord") { }
        val triggerRefund = NamedAction<Any>("TriggerRefund") { }

        val transitionTable = ExTransitionTable.Builder<PaymentState, String>()
            // NEW -> PENDING_VERIFICATION
            .from(PaymentState.NEW)
            .onEvent("START_PAYMENT")
            .to(PaymentState.PENDING_VERIFICATION)
            .action(logTransaction)
            .action(sendEmail)
            .postAction(createAuditRecord)
            .end()
            // PENDING_VERIFICATION -> VERIFIED (low risk)
            .from(PaymentState.PENDING_VERIFICATION)
            .onEvent("VERIFY")
            .toMultiple()
            .to(PaymentState.VERIFIED)
            .condition(isLowAmount)
            .condition(isVerified)
            .action(updateDatabase)
            .action(notifyUser)
            .timeout(Timeout(2))
            .end()
            .to(PaymentState.FAILED)
            .condition(isFraudDetected)
            .action(sendEmail)
            .action(logTransaction)
            .end()
            .to(PaymentState.PENDING_VERIFICATION)
            .condition(isHighRisk)
            .action(sendSms)
            .timeout(Timeout(5))
            .end()
            .endMultiple()
            // VERIFIED -> PROCESSING
            .from(PaymentState.VERIFIED)
            .onEvent("PROCESS")
            .to(PaymentState.PROCESSING)
            .action(chargeCard)
            .action(logTransaction)
            .postAction(sendEmail)
            .end()
            // PROCESSING -> COMPLETED
            .from(PaymentState.PROCESSING)
            .onEvent("COMPLETE")
            .to(PaymentState.COMPLETED)
            .action(updateDatabase)
            .action(notifyUser)
            .action(releaseHold)
            .postAction(createAuditRecord)
            .end()
            // PROCESSING -> FAILED
            .from(PaymentState.PROCESSING)
            .onEvent("FAIL")
            .to(PaymentState.FAILED)
            .action(logTransaction)
            .action(sendEmail)
            .postAction(createAuditRecord)
            .end()
            // FAILED -> REFUNDED
            .from(PaymentState.FAILED)
            .onEvent("REFUND")
            .to(PaymentState.REFUNDED)
            .action(triggerRefund)
            .action(updateDatabase)
            .action(notifyUser)
            .postAction(createAuditRecord)
            .end()
            .build()

        println("\n========== PlantUML with named Actions and Conditions ==========")
        println(transitionTable.toPlantUml())
        println("===================================================================\n")

        println("\n========== Mermaid with named Actions and Conditions ==========")
        println(transitionTable.toMermaid())
        println("=================================================================\n")
    }

    @Test
    fun `should generate diagram with mixed named and unnamed actions`() {
        var counter = 0

        val transitionTable = ExTransitionTable.Builder<PaymentState, String>()
            .from(PaymentState.NEW)
            .onEvent("START")
            .to(PaymentState.PENDING_VERIFICATION)
            .action(NamedAction("ValidateInput") { counter++ })
            .action { counter++ } // Unnamed action
            .action(NamedAction("InitSession") { counter++ })
            .end()
            .build()

        println("\n========== PlantUML with mixed Actions ==========")
        println(transitionTable.toPlantUml())
        println("====================================================\n")

        println("\n========== Mermaid with mixed Actions ==========")
        println(transitionTable.toMermaid())
        println("===================================================\n")
    }
}
