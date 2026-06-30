package io.github.ngirchev.fsm.example.spring.integration;

import io.github.ngirchev.fsm.example.spring.domain.ExternalCallResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DefaultExternalServiceClientConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ExternalServiceClient externalServiceClient() {
        return workflow -> ExternalCallResult.DONE;
    }
}
