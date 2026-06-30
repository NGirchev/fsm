package io.github.ngirchev.fsm.example.spring.integration;

import io.github.ngirchev.fsm.example.spring.domain.ExternalCallResult;
import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflow;

public interface ExternalServiceClient {

    ExternalCallResult submit(ExternalWorkflow workflow);
}
