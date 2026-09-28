package io.github.ngirchev.fsm.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ngirchev.fsm.example.ApiExceptionHandler.ApiError;
import io.github.ngirchev.fsm.spring.definition.FlowDefinition;
import io.github.ngirchev.fsm.spring.definition.FlowService;
import io.github.ngirchev.fsm.spring.definition.FlowVersion;
import io.github.ngirchev.fsm.spring.definition.FlowVersionStatus;
import io.github.ngirchev.fsm.example.order.*;
import io.github.ngirchev.fsm.serialization.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.github.ngirchev.fsm.example.order.OrderController.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FsmExampleApplicationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private FlowService flowService;
    @Autowired
    private OrderRepository orders;

    @Test
    void amountGuardSelectsBranchAndRunsLogAction() throws Exception {
        var original = flowService.active("order").definition();
        try {
            publish(new FlowDefinition("NEW", new FsmDto(false, Map.of(
                    "NEW", List.of(new TransitionDto("NEW", new ToDto("IN_PROGRESS", List.of(), List.of(), List.of(), null), "SUBMIT")),
                    "IN_PROGRESS", List.of(
                            new TransitionDto("IN_PROGRESS", new ToDto("SENT", List.of("amountBelowCommissionThreshold"), List.of(),
                                    List.of("logOrderNotification"), null), "FINISH"),
                            new TransitionDto("IN_PROGRESS", new ToDto("FAILED", List.of("amountAtLeastCommissionThreshold"), List.of(),
                                    List.of(), null), "FINISH")),
                    "SENT", List.of(), "FAILED", List.of()))));
            for (var amount : List.of(new BigDecimal("100.00"), new BigDecimal("1000.00"))) {
                var order = post("/api/orders", new CreateOrderRequest(amount), OrderResponse.class);
                post("/api/orders/" + order.id() + "/events", new OrderEventRequest("SUBMIT"), OrderResponse.class);
                var result = rest.postForEntity(url("/api/orders/" + order.id() + "/events"),
                        new OrderEventRequest("FINISH"), String.class);
                assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
                var stored = rest.getForObject(url("/api/orders/" + order.id()), OrderResponse.class);
                boolean belowThreshold = amount.compareTo(new BigDecimal("1000.00")) < 0;
                assertThat(stored.state()).isEqualTo(belowThreshold ? "SENT" : "FAILED");
            }
        } finally {
            publish(original);
        }
    }

    @Test
    void editorPageAndVersionApiPersistVisualLayout() throws Exception {
        var page = rest.getForEntity(url("/fsm-editor/"), String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("<title>FSM Visual Editor</title>", "./assets/");
        var original = flowService.active("order").definition();
        var editor = objectMapper.readTree("""
                {"name":"Order", "states":[{"id":"new","label":"NEW",
                "position":{"x":-120.5,"y":450.25}}]}
                """);
        var definition = new FlowDefinition(original.initialState(), original.table(), editor);
        String path = "/api/flows/editor-layout/versions";
        var draft = post(path, definition, FlowVersion.class);
        editor.withObject("/states/0/position").put("x", 987.25);
        var updated = rest.exchange(url(path + "/" + draft.version()), HttpMethod.PUT,
                new HttpEntity<>(definition), FlowVersion.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        var loaded = rest.getForObject(url(path + "/" + draft.version()), FlowVersion.class);
        assertThat(loaded.definition().editor()).isEqualTo(editor);
        var published = post(path + "/" + draft.version() + "/publish", null, FlowVersion.class);
        assertThat(published.definition().editor()).isEqualTo(editor);
        assertThat(published.definition().table()).isEqualTo(original.table());
    }

    @Test
    void creationPersistsInitialAutomaticBehaviorAndRollsBackChainExceedingLimit() throws Exception {
        var original = flowService.active("order").definition();
        var automatic = new FlowDefinition("NEW", new FsmDto(true, Map.of(
                "NEW", List.of(new TransitionDto("NEW", new ToDto(
                        "IN_PROGRESS", List.of(), List.of(), List.of(), null), null)),
                "IN_PROGRESS", List.of(new TransitionDto("IN_PROGRESS",
                        new ToDto("COMPLETED", List.of(), List.of(), List.of(), null), "FINISH"))
        ), 1));
        try {
            var published = publish(automatic);
            var created = post("/api/orders", new CreateOrderRequest(new BigDecimal("100.00")), OrderResponse.class);
            assertThat(created.state()).isEqualTo("IN_PROGRESS");
            assertThat(created.flowVersion()).isEqualTo(published.version());
            assertThat(created.trace()).hasSize(1);
            assertThat(created.trace().get(0).from()).isEqualTo("NEW");
            assertThat(created.trace().get(0).to()).isEqualTo("IN_PROGRESS");
            assertThat(rest.getForObject(url("/api/orders/" + created.id()), OrderResponse.class))
                    .usingRecursiveComparison().ignoringFields("trace").isEqualTo(created);
            var completed = post("/api/orders/" + created.id() + "/events", new OrderEventRequest("FINISH"), OrderResponse.class);
            assertThat(completed.state()).isEqualTo("COMPLETED");

            publish(new FlowDefinition("NEW", new FsmDto(false, automatic.table().getTransitions(), 1)));
            var disabled = post("/api/orders", new CreateOrderRequest(new BigDecimal("100.00")), OrderResponse.class);
            assertThat(disabled.state()).isEqualTo("NEW");

            var cyclic = new FlowDefinition("NEW", new FsmDto(true, Map.of(
                    "NEW", List.of(new TransitionDto("NEW", new ToDto("NEW", List.of(),
                            List.of(), List.of(), null), null))
            ), 1));
            publish(cyclic);
            long countBefore = orders.count();
            var failed = rest.postForEntity(url("/api/orders"),
                    new CreateOrderRequest(new BigDecimal("100.00")), ApiError.class);
            assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(orders.count()).isEqualTo(countBefore);
        } finally {
            publish(original);
        }
    }

    @Test
    void runsSeededFlowAndAppliesNewlyPublishedVersionWithoutRestart() throws Exception {
        var original = flowService.active("order").definition();
        try {
            var order = post("/api/orders", new CreateOrderRequest(new BigDecimal("100.00")), OrderResponse.class);
            var pending = post("/api/orders/" + order.id() + "/events", new OrderEventRequest("SUBMIT"), OrderResponse.class);
            var completedSeed = post("/api/orders/" + order.id() + "/events", new OrderEventRequest("FINISH"), OrderResponse.class);
            assertThat(pending.state()).isEqualTo("IN_PROGRESS");
            assertThat(completedSeed.state()).isEqualTo("COMPLETED");

            var inProgress = post("/api/orders", new CreateOrderRequest(new BigDecimal("100.00")), OrderResponse.class);
            var inProgressPending = post("/api/orders/" + inProgress.id() + "/events",
                    new OrderEventRequest("SUBMIT"), OrderResponse.class);
            assertThat(inProgressPending.state()).isEqualTo("IN_PROGRESS");

            var draft = post("/api/flows/order/versions", fastDefinition(List.of()), FlowVersion.class);
            assertThat(draft.status()).isEqualTo(FlowVersionStatus.DRAFT);
            var published = post("/api/flows/order/versions/" + draft.version() + "/publish", null, FlowVersion.class);
            assertThat(published.status()).isEqualTo(FlowVersionStatus.ACTIVE);

            var inProgressCompleted = post("/api/orders/" + inProgress.id() + "/events",
                    new OrderEventRequest("FINISH"), OrderResponse.class);
            assertThat(inProgressCompleted.state()).isEqualTo("COMPLETED");
            assertThat(inProgressCompleted.flowVersion()).isEqualTo(inProgress.flowVersion());

            var newOrder = post("/api/orders", new CreateOrderRequest(new BigDecimal("100.00")), OrderResponse.class);
            assertThat(newOrder.flowVersion()).isEqualTo(published.version());
            var completed = post("/api/orders/" + newOrder.id() + "/events",
                    new OrderEventRequest("FAST_COMPLETE"), OrderResponse.class);
            assertThat(completed.state()).isEqualTo("COMPLETED");

            var invalidDraft = post("/api/flows/order/versions", fastDefinition(List.of("missingBean")), FlowVersion.class);
            var invalidPublish = rest.postForEntity(
                    url("/api/flows/order/versions/" + invalidDraft.version() + "/publish"), null, ApiError.class);
            assertThat(invalidPublish.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(invalidPublish.getBody().message()).contains("missingBean");

            var stillWorks = post("/api/orders", new CreateOrderRequest(new BigDecimal("100.00")), OrderResponse.class);
            var stillCompleted = post("/api/orders/" + stillWorks.id() + "/events",
                    new OrderEventRequest("FAST_COMPLETE"), OrderResponse.class);
            assertThat(stillCompleted.state()).isEqualTo("COMPLETED");
        } finally {
            publish(original);
        }
    }

    @Test
    void activeVersionCannotBeOverwritten() {
        var response = rest.exchange(url("/api/flows/order/versions/1"), HttpMethod.PUT,
                new HttpEntity<>(fastDefinition(List.of())), ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void stateTooLongForOrderColumnCannotReplaceActiveFlow() throws Exception {
        var active = flowService.active("order");
        var tooLong = new FlowDefinition("NEW", new FsmDto(false, Map.of(
                "NEW", List.of(new TransitionDto("NEW", new ToDto("A".repeat(121),
                        List.of(), List.of(), List.of(), null), "GO"))
        )));
        var draft = post("/api/flows/order/versions", tooLong, FlowVersion.class);
        var response = rest.postForEntity(url("/api/flows/order/versions/" + draft.version() + "/publish"),
                null, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(flowService.active("order")).isEqualTo(active);
        assertThat(flowService.get("order", draft.version()).status()).isEqualTo(FlowVersionStatus.DRAFT);
    }

    @Test
    void onlyDraftsCanBeEditedOrPublished() throws Exception {
        String path = "/api/flows/flow-store-errors/versions";
        var definition = fastDefinition(List.of());
        var draft = post(path, definition, FlowVersion.class);

        assertThat(rest.postForEntity(url(path + "/2/publish"), null, ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.exchange(url(path + "/2"), HttpMethod.PUT,
                new HttpEntity<>(definition), ApiError.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        post(path + "/" + draft.version() + "/publish", null, FlowVersion.class);
        assertThat(rest.postForEntity(url(path + "/" + draft.version() + "/publish"), null, ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rest.exchange(url(path + "/" + draft.version()), HttpMethod.PUT,
                new HttpEntity<>(definition), ApiError.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(flowService.active("flow-store-errors").version()).isEqualTo(draft.version());
        assertThat(flowService.list("flow-store-errors")).hasSize(1);
    }

    @Test
    void concurrentPublicationsLeaveOneActiveVersion() throws Exception {
        String key = "concurrent-publish";
        var first = flowService.createDraft(key, fastDefinition(List.of()));
        var second = flowService.createDraft(key, fastDefinition(List.of()));
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> {
                start.await();
                return flowService.publish(key, first.version());
            });
            var two = executor.submit(() -> {
                start.await();
                return flowService.publish(key, second.version());
            });
            start.countDown();
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);
            assertThat(flowService.list(key).stream().filter(version -> version.status() == FlowVersionStatus.ACTIVE))
                    .hasSize(1);
            assertThat(flowService.list(key).stream().filter(version -> version.status() == FlowVersionStatus.ARCHIVED))
                    .hasSize(1);
            assertThat(flowService.active(key).status()).isEqualTo(FlowVersionStatus.ACTIVE);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void newFlowDraftCanBeEditedAndPublished() throws Exception {
        String path = "/api/flows/jpa-example/versions";
        var draft = post(path, fastDefinition(List.of()), FlowVersion.class);
        assertThat(draft.version()).isEqualTo(1);
        assertThat(draft.status()).isEqualTo(FlowVersionStatus.DRAFT);

        var updatedDefinition = new FlowDefinition("NEW", new FsmDto(false, Map.of(
                "NEW", List.of(new TransitionDto("NEW",
                        new ToDto("COMPLETED", List.of(), List.of(), List.of(), null), "DONE"))
        )));
        var updated = rest.exchange(url(path + "/1"), HttpMethod.PUT,
                new HttpEntity<>(updatedDefinition), FlowVersion.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().definition()).isEqualTo(updatedDefinition);

        var published = post(path + "/1/publish", null, FlowVersion.class);
        assertThat(published.status()).isEqualTo(FlowVersionStatus.ACTIVE);
        assertThat(flowService.active("jpa-example").definition()).isEqualTo(updatedDefinition);

        var next = post(path, fastDefinition(List.of()), FlowVersion.class);
        assertThat(next.version()).isEqualTo(2);
        post(path + "/2/publish", null, FlowVersion.class);
        assertThat(flowService.get("jpa-example", 1).status()).isEqualTo(FlowVersionStatus.ARCHIVED);
    }

    @Test
    void concurrentDraftsForNewFlowReceiveDifferentVersions() throws Exception {
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                return flowService.createDraft("concurrent-flow", fastDefinition(List.of())).version();
            });
            var second = executor.submit(() -> {
                start.await();
                return flowService.createDraft("concurrent-flow", fastDefinition(List.of())).version();
            });
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(1, 2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void missingOrderAndFlowReturnNotFound() {
        assertThat(rest.getForEntity(url("/api/orders/999999"), ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity(url("/api/flows/missing/versions/1"), ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }


    @Test
    void flowDefinitionRequiresInitialStateAndTable() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        for (String json : List.of("{}", "{\"initialState\":\"NEW\",\"table\":null}",
                "{\"initialState\":null,\"table\":{\"autoTransitionEnabled\":false,\"transitions\":{}}}")) {
            var response = rest.postForEntity(url("/api/flows/order/versions"), new HttpEntity<>(json, headers), ApiError.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private FlowDefinition fastDefinition(List<String> guards) {
        return new FlowDefinition("NEW", new FsmDto(false, Map.of(
                "NEW", List.of(new TransitionDto("NEW",
                        new ToDto("COMPLETED", guards, List.of(), List.of(), null), "FAST_COMPLETE"))
        )));
    }

    private FlowVersion publish(FlowDefinition definition) throws Exception {
        var draft = post("/api/flows/order/versions", definition, FlowVersion.class);
        return post("/api/flows/order/versions/" + draft.version() + "/publish", null, FlowVersion.class);
    }

    private <T> T post(String path, Object body, Class<T> responseType) throws Exception {
        var response = rest.postForEntity(url(path), body, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("Unexpected response: %s %s", response.getStatusCode(), response.getBody()).isTrue();
        return objectMapper.readValue(response.getBody(), responseType);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
