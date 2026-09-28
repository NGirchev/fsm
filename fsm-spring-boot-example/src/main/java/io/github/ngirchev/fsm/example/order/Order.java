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
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static io.github.ngirchev.fsm.example.order.OrderController.*;

@Getter
@Entity(name = "PurchaseOrder")
@Table(name = "orders")
@NoArgsConstructor
public class Order implements StateContext<String> {
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Setter
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal commission = new BigDecimal("0.00");
    @Setter
    @Transient
    @Getter(onMethod_ = @JsonIgnore)
    private OrderEvent event;
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
    // In-memory FSM transition; excluded from JSON and database storage.
    @Setter
    @Transient
    @Getter(onMethod_ = @JsonIgnore)
    private Transition<String> currentTransition;

    @Transient
    private final java.util.List<OrderTrace> trace = new java.util.ArrayList<>();

    public Order(String state, int flowVersion, BigDecimal amount) {
        this.state = state;
        this.flowVersion = flowVersion;
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (amount.signum() < 0 || amount.scale() > 2 || amount.precision() - amount.scale() > 17) {
            throw new IllegalArgumentException("amount must be non-negative with at most 17 integer and 2 decimal digits");
        }
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public OrderResponse toResponse() {
        return new OrderResponse(id, state, flowVersion, amount, commission, java.util.List.copyOf(trace));
    }
}
