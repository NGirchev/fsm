package io.github.ngirchev.fsm.example.spring.service;

import io.github.ngirchev.fsm.example.spring.domain.ExternalCallResult;
import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowRepository;
import io.github.ngirchev.fsm.example.spring.integration.ExternalServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.atomic.AtomicReference;

import static io.github.ngirchev.fsm.example.spring.domain.ExternalCallResult.DONE;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalCallResult.FAILED;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.AWAITING_EXTERNAL_SERVICE_RESULT;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.END;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.EXTERNAL_SERVICE_DONE;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.EXTERNAL_SERVICE_FAILED;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.NEW;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.NOTIFY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "fsm.example.runner.enabled=false")
class ExternalWorkflowServiceTest {

    @Autowired
    private ExternalWorkflowService service;

    @Autowired
    private ExternalWorkflowRepository repository;

    @Autowired
    private ControllableExternalServiceClient externalServiceClient;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        externalServiceClient.reset();
    }

    @Test
    void donePathCommitsEveryStatusAsEnversRevision() {
        externalServiceClient.nextResult(DONE);

        Long workflowId = service.createWorkflow();
        service.start(workflowId);

        assertThat(service.currentStatus(workflowId)).isEqualTo(END);
        assertThat(service.statusHistory(workflowId))
                .containsExactly(
                        NEW,
                        AWAITING_EXTERNAL_SERVICE_RESULT,
                        EXTERNAL_SERVICE_DONE,
                        NOTIFY,
                        END
                );
        assertThat(service.statusRevisionNumbers(workflowId))
                .hasSize(5)
                .doesNotHaveDuplicates()
                .isSorted();
    }

    @Test
    void failedPathCommitsEveryStatusAsEnversRevision() {
        externalServiceClient.nextResult(FAILED);

        Long workflowId = service.createWorkflow();
        service.start(workflowId);

        assertThat(service.currentStatus(workflowId)).isEqualTo(END);
        assertThat(service.statusHistory(workflowId))
                .containsExactly(
                        NEW,
                        AWAITING_EXTERNAL_SERVICE_RESULT,
                        EXTERNAL_SERVICE_FAILED,
                        NOTIFY,
                        END
                );
    }

    @Test
    void failedExternalCallDoesNotCommitAwaitingStatus() {
        externalServiceClient.nextFailure(new IllegalStateException("external service is unavailable"));

        Long workflowId = service.createWorkflow();

        assertThatThrownBy(() -> service.start(workflowId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("external service is unavailable");
        assertThat(service.currentStatus(workflowId)).isEqualTo(NEW);
        assertThat(service.statusHistory(workflowId)).containsExactly(NEW);
        assertThat(service.statusRevisionNumbers(workflowId)).hasSize(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExternalServiceClientTestConfiguration {

        @Bean
        @Primary
        ControllableExternalServiceClient controllableExternalServiceClient() {
            return new ControllableExternalServiceClient();
        }
    }

    static class ControllableExternalServiceClient implements ExternalServiceClient {

        private final AtomicReference<ExternalCallResult> result = new AtomicReference<>(DONE);
        private final AtomicReference<RuntimeException> failure = new AtomicReference<>();

        void reset() {
            result.set(DONE);
            failure.set(null);
        }

        void nextResult(ExternalCallResult result) {
            this.result.set(result);
        }

        void nextFailure(RuntimeException failure) {
            this.failure.set(failure);
        }

        @Override
        public ExternalCallResult submit(io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflow workflow) {
            RuntimeException exception = failure.getAndSet(null);
            if (exception != null) {
                throw exception;
            }
            return result.get();
        }
    }
}
