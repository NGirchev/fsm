package io.github.ngirchev.fsm.example.workflow;

import io.github.ngirchev.fsm.StateContext;
import io.github.ngirchev.fsm.Transition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "notification_workflow")
@Getter
public class ExternalWorkflow implements StateContext<ExternalWorkflow.State> {

    // NEW waits for start(); NOTIFY is pending background work; END is finished.
    public enum State {NEW, NOTIFY, END}

    // Database ID, also used to build the notification's stable retry key.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FSM changes this business state; JPA persists it with the current transaction.
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    // Current in-memory transition for FSM actions; never stored in the database.
    @Setter
    @Transient
    private Transition<State> currentTransition;

    public ExternalWorkflow() {
        state = State.NEW;
    }
}
