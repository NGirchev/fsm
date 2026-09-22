package io.github.ngirchev.fsm.example.workflow;

import io.github.ngirchev.fsm.FsmFactory;
import io.github.ngirchev.fsm.impl.extended.ExDomainFsm;
import io.github.ngirchev.fsm.spring.worker.FsmTaskProcessor;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static io.github.ngirchev.fsm.example.workflow.ExternalWorkflow.State.NEW;
import static io.github.ngirchev.fsm.example.workflow.ExternalWorkflow.State.NOTIFY;
import static io.github.ngirchev.fsm.example.workflow.ExternalWorkflow.State.END;
import static io.github.ngirchev.fsm.example.workflow.ExternalWorkflowEvent.START;
import static io.github.ngirchev.fsm.example.workflow.ExternalWorkflowEvent.ADVANCE;

@Configuration(proxyBeanMethods = false)
public class ExternalWorkflowConfiguration {

    // Shared transition rules: the service sends START, then the worker sends ADVANCE.
    @Bean
    ExDomainFsm<ExternalWorkflow, ExternalWorkflow.State, ExternalWorkflowEvent> externalWorkflowFsm(
            WorkflowNotificationClient notificationClient
    ) {
        return FsmFactory.INSTANCE.<ExternalWorkflow.State, ExternalWorkflowEvent>statesWithEvents()
                .from(NEW).onEvent(START).to(NOTIFY).end()
                .from(NOTIFY).onEvent(ADVANCE).to(END)
                .action(context -> {
                    ExternalWorkflow workflow = (ExternalWorkflow) context;
                    notificationClient.notify(workflow, "external-workflow:" + workflow.getId() + ":notification");
                })
                .end()
                .build()
                .createDomainFsm();
    }

    // The starter discovers this bean and calls processNext() on each background poll.
    @Bean
    FsmTaskProcessor<ExternalWorkflow> externalWorkflowTaskProcessor(
            ExternalWorkflowRepository repository,
            ExDomainFsm<ExternalWorkflow, ExternalWorkflow.State, ExternalWorkflowEvent> fsm
    ) {
        // One transaction claims a NOTIFY row, executes the FSM action and saves END.
        return new FsmTaskProcessor<>(repository, workflow -> fsm.handle(workflow, ADVANCE));
    }

    // Demo external client: logs instead of sending a real notification.
    @Bean
    @ConditionalOnMissingBean
    WorkflowNotificationClient workflowNotificationClient() {
        return (workflow, idempotencyKey) ->
                LoggerFactory.getLogger(WorkflowNotificationClient.class)
                        .info("Demo notification for workflow {}, key={}", workflow.getId(), idempotencyKey);
    }

    // Spring runs this once at startup when FSM_DEMO_RUNNER_ENABLED=true.
    @Bean
    @ConditionalOnProperty(prefix = "fsm.example.runner", name = "enabled", havingValue = "true")
    CommandLineRunner externalWorkflowDemoRunner(ExternalWorkflowService service) {
        return args -> {
            Long id = service.createWorkflow();
            service.start(id);
        };
    }
}
