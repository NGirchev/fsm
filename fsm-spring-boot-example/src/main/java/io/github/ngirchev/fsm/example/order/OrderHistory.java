package io.github.ngirchev.fsm.example.order;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_history")
@Getter
@NoArgsConstructor
public class OrderHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private long orderId;
    private int flowVersion;
    private String kind;
    private String fromState;
    private String toState;
    private String event;
    private String source;
    private String requestId;

    public OrderHistory(Order order, String kind, String from, String to) {
        this.orderId = order.getId();
        this.flowVersion = order.getFlowVersion();
        this.kind = kind;
        this.fromState = from;
        this.toState = to;
        if (order.getEvent() != null) {
            this.event = order.getEvent().eventType();
            this.source = order.getEvent().source();
            this.requestId = order.getEvent().requestId();
        }
    }
}
