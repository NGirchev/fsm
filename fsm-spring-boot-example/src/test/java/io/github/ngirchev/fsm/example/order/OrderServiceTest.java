package io.github.ngirchev.fsm.example.order;

import io.github.ngirchev.fsm.example.ApiExceptionHandler;
import io.github.ngirchev.fsm.spring.definition.FlowDefinition;
import io.github.ngirchev.fsm.spring.definition.FlowLoader;
import io.github.ngirchev.fsm.spring.definition.FlowService;
import io.github.ngirchev.fsm.spring.definition.FlowVersion;
import io.github.ngirchev.fsm.spring.definition.FlowVersionStatus;
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable;
import io.github.ngirchev.fsm.serialization.FsmDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final FlowService flows = mock(FlowService.class);
    private final FlowLoader loader = mock(FlowLoader.class);
    private final OrderService service = new OrderService(orders, flows,
            new OrderExecution(loader, Map.of(), Map.of(), new io.github.ngirchev.fsm.spring.FsmBeanRegistry(Map.of(), Map.of()),
                    mock(OrderFlowValidator.class)), mock(OrderHistoryRepository.class));

    @Test
    void creationExecutesInitialAutomaticTransition() {
        var table = new ExTransitionTable.Builder<String, OrderEvent>()
                .autoTransitionEnabled(true).maxImmediateAutoTransitions(1)
                .add("NEW", null, "IN_PROGRESS", null, null, null, null)
                .build();
        var order = new Order("NEW", 2, new BigDecimal("100.00"));
        var definition = new FlowDefinition("NEW", new FsmDto(false, Map.of()));
        when(flows.active("order")).thenReturn(new FlowVersion("order", 2, FlowVersionStatus.ACTIVE, definition));
        doReturn(table).when(loader).load(eq(definition), any());
        when(orders.saveAndFlush(any(Order.class))).thenReturn(order);

        var created = service.create(new BigDecimal("100.00"));
        assertThat(created.getState()).isEqualTo("IN_PROGRESS");
        verify(orders).saveAndFlush(any(Order.class));
    }

    @Test
    void stringEventOverloadStillExecutesThePinnedDefinition() {
        var definition = new FlowDefinition("NEW", new FsmDto(false, Map.of()));
        var table = new ExTransitionTable.Builder<String, OrderEvent>()
                .add("NEW", new OrderEvent("GO", null, null), "DONE", null, null, null, null).build();
        var order = new Order("NEW", 2, new BigDecimal("100.00"));
        when(orders.findByIdForUpdate(10L)).thenReturn(java.util.Optional.of(order));
        when(flows.get("order", 2)).thenReturn(new FlowVersion("order", 2, FlowVersionStatus.ARCHIVED, definition));
        doReturn(table).when(loader).load(eq(definition), any());
        assertThat(service.handle(10L, "GO").getState()).isEqualTo("DONE");
    }

    @Test
    void missingEventReturnsBadRequest() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new OrderController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        for (String payload : List.of("{}", "{\"event\":null}")) {
            mvc.perform(post("/api/orders/1/events").contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(orders, flows, loader);
    }

    @Test
    void missingAmountReturnsBadRequestWithoutCreatingAnOrder() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new OrderController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/orders")).andExpect(status().isBadRequest());
        for (String payload : List.of("{}", "{\"amount\":null}")) {
            mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(orders, flows, loader);
    }
}
