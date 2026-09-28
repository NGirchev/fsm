package io.github.ngirchev.fsm.example.order;

import io.github.ngirchev.fsm.serialization.FsmDto;
import io.github.ngirchev.fsm.spring.admin.FsmAdminRegistration;
import io.github.ngirchev.fsm.spring.definition.FlowDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class OrderAdminConfiguration {
    @Bean
    FsmAdminRegistration orderAdminRegistration(OrderFlowValidator validator) {
        var initial = new FlowDefinition("NEW", new FsmDto(false, Map.of("NEW", List.of())));
        return new FsmAdminRegistration("order", "Order flow", initial,
                OrderEvent::validateFlowEvents, validator::validate);
    }
}
