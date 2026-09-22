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
    private final OrderService service = new OrderService(orders, flows, loader);

    @Test
    void creationExecutesInitialAutomaticTransition() {
        var table = new ExTransitionTable.Builder<String, String>()
                .autoTransitionEnabled(true).maxImmediateAutoTransitions(1)
                .add("NEW", null, "IN_PROGRESS", null, null, null, null)
                .build();
        var order = new Order("NEW", 2);
        var definition = new FlowDefinition("NEW", new FsmDto(false, Map.of()));
        when(flows.active("order")).thenReturn(new FlowVersion("order", 2, FlowVersionStatus.ACTIVE, definition));
        when(loader.load(definition)).thenReturn(table);
        when(orders.saveAndFlush(any(Order.class))).thenReturn(order);

        var created = service.create();
        assertThat(created.getState()).isEqualTo("IN_PROGRESS");
        verify(orders).saveAndFlush(any(Order.class));
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
}
