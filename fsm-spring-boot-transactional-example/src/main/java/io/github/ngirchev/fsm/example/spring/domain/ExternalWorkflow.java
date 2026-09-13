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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

@Entity
@Audited
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalWorkflow implements StateContext<ExternalWorkflowStatus> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter
    private ExternalWorkflowStatus state;

    @Enumerated(EnumType.STRING)
    @Setter
    private ExternalCallResult externalResult;

    @Column(nullable = false)
    private boolean notificationSent;

    @Transient
    @Setter
    private Transition<ExternalWorkflowStatus> currentTransition;

    public ExternalWorkflow(ExternalWorkflowStatus state) {
        this.state = state;
    }

    public void markNotificationSent() {
        this.notificationSent = true;
    }

    public void synchronizeVersionFrom(ExternalWorkflow persistedWorkflow) {
        if (!id.equals(persistedWorkflow.id)) {
            throw new IllegalArgumentException("Cannot synchronize versions of different workflows");
        }
        version = persistedWorkflow.version;
    }
}
