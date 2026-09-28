package io.github.ngirchev.fsm.example.order;

import io.github.ngirchev.fsm.Action;
import io.github.ngirchev.fsm.Guard;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

@Configuration(proxyBeanMethods = false)
public class OrderBehaviorConfiguration {
    private static final java.math.BigDecimal COMMISSION_THRESHOLD = new java.math.BigDecimal("1000.00");
    private static final java.math.BigDecimal HIGH_AMOUNT_THRESHOLD = new java.math.BigDecimal("10000.00");

    @Bean @Description("Amount is less than 1000")
    Guard<Order> amountBelowCommissionThreshold() { return order -> order.getAmount().compareTo(COMMISSION_THRESHOLD) < 0; }
    @Bean @Description("Amount is 1000 or more")
    Guard<Order> amountAtLeastCommissionThreshold() { return order -> order.getAmount().compareTo(COMMISSION_THRESHOLD) >= 0; }
    @Bean @Description("Amount is greater than 10000")
    Guard<Order> amountAbove10000() { return order -> order.getAmount().compareTo(HIGH_AMOUNT_THRESHOLD) > 0; }
    @Bean @Description("Set commission to 2% of amount")
    Action<Order> commissionTwoPercent() { return commission("0.02"); }
    @Bean @Description("Set commission to 1% of amount")
    Action<Order> commissionOnePercent() { return commission("0.01"); }

    private static Action<Order> commission(String rate) {
        var fraction = new java.math.BigDecimal(rate);
        return order -> order.setCommission(order.getAmount().multiply(fraction).setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @Bean
    @Description("Record every state change and event data")
    io.github.ngirchev.fsm.StateChangeListener<String> recordOrderHistory(OrderHistoryRepository history) {
        return (context, from, to) -> history.save(new OrderHistory((Order) context, "STATE_CHANGED", from, to));
    }

    @Bean
    @Description("Write a demo notification to the application log")
    Action<Order> logOrderNotification() {
        return order -> LoggerFactory.getLogger(OrderBehaviorConfiguration.class)
                .info("Demo notification for order {}, state={}", order.getId(), order.getState());
    }
}
