package io.github.ngirchev.fsm.example.order;

import io.github.ngirchev.fsm.Action;
import io.github.ngirchev.fsm.Guard;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OrderBehaviorConfiguration {
    @Bean
    Guard<Order> orderApproved() {
        return Order::isApproved;
    }

    @Bean
    Guard<Order> orderNotApproved() {
        return order -> !order.isApproved();
    }

    @Bean
    Action<Order> notifyOrderCompleted() {
        return order -> {
            // Demonstration only: no external message is sent. Post-action sees the completed state.
            LoggerFactory.getLogger(OrderBehaviorConfiguration.class)
                    .info("Demo completion notification for order {}, state={}", order.getId(), order.getState());
            order.setNotificationSent(true);
        };
    }
}
