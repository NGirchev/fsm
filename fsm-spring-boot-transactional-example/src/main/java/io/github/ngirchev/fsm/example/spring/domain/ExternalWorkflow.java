package io.github.ngirchev.fsm.example.spring.domain;

import io.github.ngirchev.fsm.StateContext;
import io.github.ngirchev.fsm.Transition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import org.hibernate.envers.Audited;

@Entity
@Audited
public class ExternalWorkflow implements StateContext<ExternalWorkflowStatus> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExternalWorkflowStatus state;

    @Enumerated(EnumType.STRING)
    private ExternalCallResult externalResult;

    @Column(nullable = false)
    private boolean notificationSent;

    @Transient
    private Transition<ExternalWorkflowStatus> currentTransition;

    protected ExternalWorkflow() {
    }

    public ExternalWorkflow(ExternalWorkflowStatus state) {
        this.state = state;
    }

    public Long getId() {
        return id;
    }

    @Override
    public ExternalWorkflowStatus getState() {
        return state;
    }

    @Override
    public void setState(ExternalWorkflowStatus state) {
        this.state = state;
    }

    public ExternalCallResult getExternalResult() {
        return externalResult;
    }

    public void setExternalResult(ExternalCallResult externalResult) {
        this.externalResult = externalResult;
    }

    public boolean isNotificationSent() {
        return notificationSent;
    }

    public void markNotificationSent() {
        this.notificationSent = true;
    }

    @Override
    public Transition<ExternalWorkflowStatus> getCurrentTransition() {
        return currentTransition;
    }

    @Override
    public void setCurrentTransition(Transition<ExternalWorkflowStatus> currentTransition) {
        this.currentTransition = currentTransition;
    }
}
