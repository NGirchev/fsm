package io.github.ngirchev.fsm.example.order;

import java.math.BigDecimal;
import java.util.List;

/** Actual transitions from one committed request, including synchronous automatic transitions. */
public record OrderTrace(String from, String to, String event, List<String> conditions,
                         List<String> actions, List<String> postActions, Snapshot before, Snapshot after) {
    public record Snapshot(String state, BigDecimal commission) {
        public static Snapshot of(Order order) {
            return new Snapshot(order.getState(), order.getCommission());
        }
    }
}
