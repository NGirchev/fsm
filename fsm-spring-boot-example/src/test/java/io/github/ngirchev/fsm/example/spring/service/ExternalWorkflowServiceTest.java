package io.github.ngirchev.fsm.example.spring.service;

import io.github.ngirchev.fsm.example.spring.domain.ExternalCallResult;
import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowRepository;
import io.github.ngirchev.fsm.example.spring.integration.ExternalServiceClient;
import io.github.ngirchev.fsm.example.spring.integration.WorkflowNotificationClient;
import io.github.ngirchev.fsm.example.spring.task.ExternalWorkflowTaskProcessor;
import io.github.ngirchev.fsm.example.spring.task.ExternalWorkflowTaskRepository;
import io.github.ngirchev.fsm.exception.FsmEventSourcingTransitionFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.ngirchev.fsm.example.spring.domain.ExternalCallResult.DONE;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalCallResult.FAILED;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.AWAITING_EXTERNAL_SERVICE_RESULT;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.END;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.EXTERNAL_SERVICE_DONE;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.EXTERNAL_SERVICE_FAILED;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.NEW;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.NOTIFY;
import static io.github.ngirchev.fsm.example.spring.task.ExternalWorkflowTaskStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
        "fsm.example.runner.enabled=false",
        "fsm.tasks.enabled=false"
})
class ExternalWorkflowServiceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ExternalWorkflowService service;

    @Autowired
    private ExternalWorkflowRepository repository;

    @Autowired
    private ControllableExternalServiceClient externalServiceClient;

    @Autowired
    private ExternalWorkflowTaskProcessor taskProcessor;

    @Autowired
    private ExternalWorkflowTaskRepository taskRepository;

    @Autowired
    private ControllableWorkflowNotificationClient notificationClient;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        repository.deleteAll();
        externalServiceClient.reset();
        notificationClient.reset();
    }

    @Test
    void donePathCommitsEveryStatusAsEnversRevision() {
        externalServiceClient.nextResult(DONE);

        Long workflowId = service.createWorkflow();
        service.start(workflowId);

        assertThat(service.currentStatus(workflowId)).isEqualTo(AWAITING_EXTERNAL_SERVICE_RESULT);
        assertThat(taskRepository.countByStatus(PENDING)).isOne();
        assertThat(service.statusHistory(workflowId)).containsExactly(NEW, AWAITING_EXTERNAL_SERVICE_RESULT);

        assertThat(processAllTasks()).isEqualTo(3);
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

        assertThat(processAllTasks()).isEqualTo(3);
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
    void eachTaskUsesItsOwnTransactionWhenDrainedInsideAnOuterTransaction() {
        externalServiceClient.nextResult(DONE);
        Long workflowId = service.createWorkflow();
        service.start(workflowId);

        new TransactionTemplate(transactionManager).executeWithoutResult(ignored ->
                assertThat(processAllTasks()).isEqualTo(3)
        );

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
    void failedExternalCallDoesNotCommitAwaitingStatus() {
        externalServiceClient.nextFailure(new IllegalStateException("external service is unavailable"));

        Long workflowId = service.createWorkflow();

        assertThatThrownBy(() -> service.start(workflowId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("external service is unavailable");
        assertThat(service.currentStatus(workflowId)).isEqualTo(NEW);
        assertThat(service.statusHistory(workflowId)).containsExactly(NEW);
        assertThat(service.statusRevisionNumbers(workflowId)).hasSize(1);
        assertThat(taskRepository.countByStatus(PENDING)).isZero();
    }

    @Test
    void concurrentStartsSubmitExternalCallOnlyOnce() throws Exception {
        Long workflowId = service.createWorkflow();
        externalServiceClient.waitForCompetingSubmission();

        var executor = Executors.newFixedThreadPool(2);
        try {
            var startGate = new CountDownLatch(1);
            var first = executor.submit(() -> {
                startGate.await();
                service.start(workflowId);
                return null;
            });
            var second = executor.submit(() -> {
                startGate.await();
                service.start(workflowId);
                return null;
            });
            startGate.countDown();

            Throwable failure = null;
            for (var start : java.util.List.of(first, second)) {
                try {
                    start.get();
                } catch (ExecutionException exception) {
                    assertThat(failure).isNull();
                    failure = exception.getCause();
                }
            }

            assertThat(failure).isInstanceOf(FsmEventSourcingTransitionFailedException.class);
            assertThat(externalServiceClient.submissionCount()).isEqualTo(1);
            assertThat(taskRepository.countByStatus(PENDING)).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedTaskRemainsPendingAndCanBeRetried() {
        externalServiceClient.nextResult(DONE);
        Long workflowId = service.createWorkflow();
        service.start(workflowId);

        assertThat(taskProcessor.processNext()).isTrue();
        assertThat(service.currentStatus(workflowId)).isEqualTo(EXTERNAL_SERVICE_DONE);
        notificationClient.nextFailure(new IllegalStateException("notification is unavailable"));

        assertThatThrownBy(taskProcessor::processNext)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("notification is unavailable");
        assertThat(service.currentStatus(workflowId)).isEqualTo(EXTERNAL_SERVICE_DONE);
        assertThat(service.statusHistory(workflowId))
                .containsExactly(NEW, AWAITING_EXTERNAL_SERVICE_RESULT, EXTERNAL_SERVICE_DONE);
        assertThat(taskRepository.countByStatus(PENDING)).isOne();

        assertThat(processAllTasks()).isEqualTo(2);
        assertThat(service.currentStatus(workflowId)).isEqualTo(END);
        assertThat(notificationClient.successfulNotificationCount()).isOne();
        assertThat(notificationClient.attemptedIdempotencyKeys()).containsExactly(
                "external-workflow:" + workflowId + ":notification",
                "external-workflow:" + workflowId + ":notification"
        );
    }

    private int processAllTasks() {
        int processedTasks = 0;
        while (taskProcessor.processNext()) {
            processedTasks++;
            if (processedTasks > 10) {
                throw new IllegalStateException("Transactional task chain did not terminate");
            }
        }
        return processedTasks;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExternalServiceClientTestConfiguration {

        @Bean
        @Primary
        ControllableExternalServiceClient controllableExternalServiceClient() {
            return new ControllableExternalServiceClient();
        }

        @Bean
        @Primary
        ControllableWorkflowNotificationClient controllableWorkflowNotificationClient() {
            return new ControllableWorkflowNotificationClient();
        }
    }

    static class ControllableExternalServiceClient implements ExternalServiceClient {

        private final AtomicReference<ExternalCallResult> result = new AtomicReference<>(DONE);
        private final AtomicReference<RuntimeException> failure = new AtomicReference<>();
        private final AtomicInteger submissions = new AtomicInteger();
        private volatile CountDownLatch competingSubmission;

        void reset() {
            result.set(DONE);
            failure.set(null);
            submissions.set(0);
            competingSubmission = null;
        }

        void waitForCompetingSubmission() {
            competingSubmission = new CountDownLatch(1);
        }

        int submissionCount() {
            return submissions.get();
        }

        void nextResult(ExternalCallResult result) {
            this.result.set(result);
        }

        void nextFailure(RuntimeException failure) {
            this.failure.set(failure);
        }

        @Override
        public ExternalCallResult submit(io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflow workflow) {
            int submission = submissions.incrementAndGet();
            CountDownLatch latch = competingSubmission;
            if (latch != null) {
                if (submission == 1) {
                    try {
                        latch.await(500, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while waiting for competing submission", exception);
                    }
                } else {
                    latch.countDown();
                }
            }
            RuntimeException exception = failure.getAndSet(null);
            if (exception != null) {
                throw exception;
            }
            return result.get();
        }
    }

    static class ControllableWorkflowNotificationClient implements WorkflowNotificationClient {

        private final AtomicReference<RuntimeException> failure = new AtomicReference<>();
        private final AtomicInteger successfulNotifications = new AtomicInteger();
        private final List<String> attemptedIdempotencyKeys = new ArrayList<>();

        void reset() {
            failure.set(null);
            successfulNotifications.set(0);
            attemptedIdempotencyKeys.clear();
        }

        void nextFailure(RuntimeException failure) {
            this.failure.set(failure);
        }

        int successfulNotificationCount() {
            return successfulNotifications.get();
        }

        List<String> attemptedIdempotencyKeys() {
            return List.copyOf(attemptedIdempotencyKeys);
        }

        @Override
        public void notify(
                io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflow workflow,
                String idempotencyKey
        ) {
            attemptedIdempotencyKeys.add(idempotencyKey);
            RuntimeException exception = failure.getAndSet(null);
            if (exception != null) {
                throw exception;
            }
            successfulNotifications.incrementAndGet();
        }
    }
}
