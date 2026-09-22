package io.github.ngirchev.fsm.example.workflow;

import io.github.ngirchev.fsm.exception.FsmEventSourcingTransitionFailedException;
import io.github.ngirchev.fsm.spring.worker.FsmTaskProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.github.ngirchev.fsm.example.workflow.ExternalWorkflow.State.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(properties = {"fsm.example.runner.enabled=false", "fsm.tasks.enabled=false"})
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
    private FsmTaskProcessor<ExternalWorkflow> processor;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockitoBean
    private WorkflowNotificationClient notificationClient;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void workerFinishesStartedWorkflow() {
        Long id = service.createWorkflow();
        service.start(id);

        assertThat(service.currentStatus(id)).isEqualTo(NOTIFY);
        verifyNoInteractions(notificationClient);

        assertThat(processor.processNext()).isTrue();

        assertThat(service.currentStatus(id)).isEqualTo(END);
        verify(notificationClient).notify(any(), eq("external-workflow:" + id + ":notification"));
        assertThat(processor.processNext()).isFalse();
        verifyNoMoreInteractions(notificationClient);
    }

    @Test
    void unstartedWorkflowIsNotPickedUp() {
        Long id = service.createWorkflow();

        assertThat(processor.processNext()).isFalse();
        assertThat(service.currentStatus(id)).isEqualTo(NEW);
        verifyNoInteractions(notificationClient);
    }

    @Test
    void rolledBackStartLeavesNothingForWorker() {
        Long id = service.createWorkflow();

        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            service.start(id);
            transaction.setRollbackOnly();
        });

        assertThat(service.currentStatus(id)).isEqualTo(NEW);
        assertThat(processor.processNext()).isFalse();
        verifyNoInteractions(notificationClient);
    }

    @Test
    void failedNotificationRollsBackAndCanBeRetriedWithSameKey() {
        Long id = service.createWorkflow();
        service.start(id);
        doThrow(new IllegalStateException("notification unavailable"))
                .doNothing().when(notificationClient).notify(any(), any());

        assertThatThrownBy(processor::processNext)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("notification unavailable");
        assertThat(service.currentStatus(id)).isEqualTo(NOTIFY);

        assertThat(processor.processNext()).isTrue();
        assertThat(service.currentStatus(id)).isEqualTo(END);
        verify(notificationClient, times(2)).notify(any(), eq("external-workflow:" + id + ":notification"));
    }

    @Test
    void repeatedStartDoesNotCreateAnotherTransition() {
        Long id = service.createWorkflow();
        service.start(id);

        assertThatThrownBy(() -> service.start(id))
                .isInstanceOf(FsmEventSourcingTransitionFailedException.class);
    }

    @Test
    void anotherWorkerSkipsWorkflowWhileItIsBeingProcessed() throws Exception {
        Long id = service.createWorkflow();
        service.start(id);
        var notificationStarted = new CountDownLatch(1);
        var releaseNotification = new CountDownLatch(1);
        doAnswer(invocation -> {
            notificationStarted.countDown();
            if (!releaseNotification.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release notification");
            }
            return null;
        }).when(notificationClient).notify(any(), any());

        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(processor::processNext);
            assertThat(notificationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(processor::processNext);
            assertThat(second.get(5, TimeUnit.SECONDS)).isFalse();
            releaseNotification.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseNotification.countDown();
            workers.shutdownNow();
        }
        assertThat(service.currentStatus(id)).isEqualTo(END);
        verify(notificationClient).notify(any(), any());
    }
}
