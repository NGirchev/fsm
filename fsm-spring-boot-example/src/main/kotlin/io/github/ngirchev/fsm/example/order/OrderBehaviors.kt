package io.github.ngirchev.fsm.example.order

import io.github.ngirchev.fsm.Action
import io.github.ngirchev.fsm.Guard
import io.github.ngirchev.fsm.StateContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OrderBehaviors {
    @Bean("paymentApproved")
    fun paymentApproved(): Guard<StateContext<String>> = Guard { context ->
        (context as Order).totalAmount.signum() > 0
    }

    @Bean("capturePayment")
    fun capturePayment(): Action<StateContext<String>> = Action { context ->
        (context as Order).paymentCaptured = true
    }

    @Bean("sendPaymentReceipt")
    fun sendPaymentReceipt(): Action<StateContext<String>> = Action { context ->
        (context as Order).receiptSent = true
    }
}
