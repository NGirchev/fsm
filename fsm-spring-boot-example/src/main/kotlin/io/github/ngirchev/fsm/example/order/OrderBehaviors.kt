package io.github.ngirchev.fsm.example.order

import io.github.ngirchev.fsm.example.flow.DynamicAction
import io.github.ngirchev.fsm.example.flow.DynamicGuard
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OrderBehaviors {
    @Bean("paymentApproved")
    fun paymentApproved(): DynamicGuard = DynamicGuard { context ->
        (context as Order).totalAmount.signum() > 0
    }

    @Bean("capturePayment")
    fun capturePayment(): DynamicAction = DynamicAction { context ->
        (context as Order).paymentCaptured = true
    }

    @Bean("sendPaymentReceipt")
    fun sendPaymentReceipt(): DynamicAction = DynamicAction { context ->
        (context as Order).receiptSent = true
    }
}
