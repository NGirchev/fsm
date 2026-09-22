package io.github.ngirchev.fsm.example.order;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.ngirchev.fsm.StateContext;
import io.github.ngirchev.fsm.Transition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static io.github.ngirchev.fsm.example.order.OrderController.*;

@Getter
@Entity(name = "PurchaseOrder")
@Table(name = "orders")
@NoArgsConstructor
public class Order implements StateContext<String> {
    // Database-generated order ID returned by POST /api/orders.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Current FSM state, loaded from and saved to the orders row.
    @Setter
    @Column(nullable = false)
    private String state;
    // Definition version fixed at creation; later publications do not change this order's rules.
    @Column(name = "flow_version", nullable = false)
    private int flowVersion;
    @Column(nullable = false)
    private boolean approved = true;

    @Setter
    @Column(name = "notification_sent", nullable = false)
    private boolean notificationSent;
    // In-memory FSM transition; excluded from JSON and database storage.
    @Setter
    @Transient
    @Getter(onMethod_ = @JsonIgnore)
    private Transition<String> currentTransition;

    public Order(String state, int flowVersion) {
        this(state, flowVersion, true);
    }

    public Order(String state, int flowVersion, boolean approved) {
        this.state = state;
        this.flowVersion = flowVersion;
        this.approved = approved;
    }

    public OrderResponse toResponse() {
        return new OrderResponse(id, state, flowVersion, approved, notificationSent);
    }
}
