package io.github.ngirchev.fsm.example.spring.fsm;

import io.github.ngirchev.fsm.FsmFactory;
import io.github.ngirchev.fsm.impl.extended.ExDomainFsm;
import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflow;
import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowEvent;
import io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus;
import io.github.ngirchev.fsm.example.spring.integration.ExternalServiceClient;
import io.github.ngirchev.fsm.example.spring.integration.WorkflowNotificationClient;
import io.github.ngirchev.fsm.example.spring.task.EnqueueNextWorkflowTaskAction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static io.github.ngirchev.fsm.example.spring.domain.ExternalCallResult.DONE;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalCallResult.FAILED;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowEvent.ADVANCE;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowEvent.START;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.AWAITING_EXTERNAL_SERVICE_RESULT;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.END;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.EXTERNAL_SERVICE_DONE;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.EXTERNAL_SERVICE_FAILED;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.NEW;
import static io.github.ngirchev.fsm.example.spring.domain.ExternalWorkflowStatus.NOTIFY;

@Configuration(proxyBeanMethods = false)
public class ExternalWorkflowFsmConfiguration {

    @Bean
    ExDomainFsm<ExternalWorkflow, ExternalWorkflowStatus, ExternalWorkflowEvent> externalWorkflowFsm(
            ExternalServiceClient externalServiceClient,
            WorkflowNotificationClient notificationClient,
            PersistWorkflowStatusAction persistWorkflowStatus,
            EnqueueNextWorkflowTaskAction enqueueNextTask
    ) {
        return FsmFactory.INSTANCE.<ExternalWorkflowStatus, ExternalWorkflowEvent>statesWithEvents()
                .from(NEW).onEvent(START).to(AWAITING_EXTERNAL_SERVICE_RESULT)
                .action(context -> {
                    ExternalWorkflow workflow = (ExternalWorkflow) context;
                    workflow.setExternalResult(externalServiceClient.submit(workflow));
                })
                .postAction(persistWorkflowStatus)
                .postAction(enqueueNextTask)
                .end()

                .from(AWAITING_EXTERNAL_SERVICE_RESULT).onEvent(ADVANCE).toMultiple()
                .to(EXTERNAL_SERVICE_DONE)
                .onCondition(context -> ((ExternalWorkflow) context).getExternalResult() == DONE)
                .postAction(persistWorkflowStatus)
                .postAction(enqueueNextTask)
                .end()
                .to(EXTERNAL_SERVICE_FAILED)
                .onCondition(context -> ((ExternalWorkflow) context).getExternalResult() == FAILED)
                .postAction(persistWorkflowStatus)
                .postAction(enqueueNextTask)
                .end()
                .endMultiple()

                .from(EXTERNAL_SERVICE_DONE).onEvent(ADVANCE).to(NOTIFY)
                .action(context -> {
                    ExternalWorkflow workflow = (ExternalWorkflow) context;
                    notificationClient.notify(workflow, notificationIdempotencyKey(workflow));
                    workflow.markNotificationSent();
                })
                .postAction(persistWorkflowStatus)
                .postAction(enqueueNextTask)
                .end()

                .from(EXTERNAL_SERVICE_FAILED).onEvent(ADVANCE).to(NOTIFY)
                .action(context -> {
                    ExternalWorkflow workflow = (ExternalWorkflow) context;
                    notificationClient.notify(workflow, notificationIdempotencyKey(workflow));
                    workflow.markNotificationSent();
                })
                .postAction(persistWorkflowStatus)
                .postAction(enqueueNextTask)
                .end()

                .from(NOTIFY).onEvent(ADVANCE).to(END)
                .postAction(persistWorkflowStatus)
                .end()

                .build()
                .createDomainFsm();
    }

    private static String notificationIdempotencyKey(ExternalWorkflow workflow) {
        return "external-workflow:" + workflow.getId() + ":notification";
    }
}
