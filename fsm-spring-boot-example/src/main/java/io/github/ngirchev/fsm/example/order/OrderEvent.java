package io.github.ngirchev.fsm.example.order;

import io.github.ngirchev.fsm.TypedEvent;
import io.github.ngirchev.fsm.spring.definition.FlowDefinition;

/** Payload does not participate in FSM matching; only eventType does. */
public record OrderEvent(String eventType, String source, String requestId) implements TypedEvent<String> {
    public OrderEvent {
        if (eventType == null || eventType.isBlank() || eventType.length() > 120) {
            throw new IllegalArgumentException("event must contain 1 to 120 characters");
        }
        if ((source != null && source.length() > 120) || (requestId != null && requestId.length() > 120)) {
            throw new IllegalArgumentException("source and requestId must contain at most 120 characters");
        }
    }

    public static void validateFlowEvents(FlowDefinition definition) {
        for (var transitions : definition.table().getTransitions().values()) {
            for (var transition : transitions) {
                if (transition.getEvent() != null) {
                    new OrderEvent(transition.getEvent(), null, null);
                }
            }
        }
    }

    @Override
    @org.springframework.lang.NonNull
    public String getEventType() { return eventType; }
}
