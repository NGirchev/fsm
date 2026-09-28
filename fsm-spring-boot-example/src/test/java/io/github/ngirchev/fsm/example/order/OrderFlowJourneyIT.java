package io.github.ngirchev.fsm.example.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ngirchev.fsm.Action;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "fsm.tasks.enabled=false")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(OrderFlowJourneyIT.ProbeConfiguration.class)
class OrderFlowJourneyIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    private static long pinned;
    private static int firstVersion;
    private static final List<String> PROBE = Collections.synchronizedList(new ArrayList<>());

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean Action<io.github.ngirchev.fsm.example.order.Order> failOrderAction() {
            return order -> { throw new IllegalStateException("Deliberate test failure"); };
        }
        @Bean Action<io.github.ngirchev.fsm.example.order.Order> beforeOrderState() {
            return order -> PROBE.add("before:" + order.getState() + ":" + order.getCommission());
        }
        @Bean Action<io.github.ngirchev.fsm.example.order.Order> afterOrderState() {
            return order -> PROBE.add("after:" + order.getState() + ":" + order.getCommission());
        }
    }

    @Test @org.junit.jupiter.api.Order(1)
    void seededOrderStillWorks() {
        var order = create("100.00");
        assertThat(order.path("flowVersion").asInt()).isEqualTo(2);
        event(order.path("id").asLong(), "SUBMIT");
        var branched = event(order.path("id").asLong(), "FINISH");
        assertThat(branched.path("state").asText()).isEqualTo("COMPLETED");
        assertThat(branched.path("trace").get(0).path("to").asText()).isEqualTo("COMMISSION_2_PERCENT");
        post("/fsm-admin/api/flows/order/versions/1/activate", null, 200);
        var direct = create("1000.00");
        assertThat(direct.path("flowVersion").asInt()).isEqualTo(1);
        event(direct.path("id").asLong(), "SUBMIT");
        var paid = event(direct.path("id").asLong(), "FINISH");
        assertThat(paid.path("state").asText()).isEqualTo("COMPLETED");
        assertThat(paid.path("commission").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(paid.path("trace").size()).isEqualTo(1);
        assertThat(paid.path("trace").get(0).path("conditions")).isEmpty();
        post("/fsm-admin/api/flows/order/versions/2/activate", null, 200);
        assertThat(get("/api/orders/" + direct.path("id").asLong()).path("flowVersion").asInt()).isEqualTo(1);
        post("/api/orders", null, 400);
        post("/api/orders", Map.of(), 400);
        post("/api/orders", Map.of("amount", "-1"), 400);
        post("/api/orders", Map.of("amount", "1.001"), 400);
    }

    @Test @org.junit.jupiter.api.Order(2)
    void firstFeeVersionAndPinnedOrder() {
        firstVersion = publish(fees("commissionTwoPercent", "commissionOnePercent", false));
        verifyFee("100.00", "2.00");
        verifyFee("1000.00", "10.00");
        pinned = create("100.00").path("id").asLong();
        event(pinned, "SUBMIT");
    }

    @Test @org.junit.jupiter.api.Order(3)
    void newFeesAndHistoryDoNotChangePinnedOrder() {
        int secondVersion = publish(fees("commissionOnePercent", "commissionTwoPercent", true));
        var old = event(pinned, "FINISH");
        assertThat(old.path("flowVersion").asInt()).isEqualTo(firstVersion);
        assertThat(old.path("commission").decimalValue()).isEqualByComparingTo("2.00");
        assertThat(old.path("state").asText()).isEqualTo("DONE");
        for (String amount : List.of("100.00", "1000.00")) {
            var id = create(amount).path("id").asLong();
            event(id, "SUBMIT");
            var done = post("/api/orders/" + id + "/events",
                    Map.of("event", "FINISH", "source", "journey", "requestId", "finish-" + id), 200);
            assertThat(done.path("commission").decimalValue()).isEqualByComparingTo(amount.equals("100.00") ? "1.00" : "20.00");
            assertThat(done.path("state").asText()).isEqualTo("DONE");
            var history = get("/api/orders/" + id + "/history");
            assertThat(history.size()).isEqualTo(2); // one persisted entry for each state change
            assertThat(history.get(1).path("source").asText()).isEqualTo("journey");
            assertThat(history.get(1).path("requestId").asText()).isEqualTo("finish-" + id);
            assertThat(history.get(1).path("fromState").asText()).isEqualTo("IN_PROGRESS");
            assertThat(history.get(1).path("toState").asText()).isEqualTo("DONE");
            long previous = 0;
            for (var entry : history) {
                assertThat(entry.path("id").asLong()).isGreaterThan(previous);
                previous = entry.path("id").asLong();
                assertThat(entry.path("flowVersion").asInt()).isEqualTo(secondVersion);
            }
        }
    }

    @Test @org.junit.jupiter.api.Order(4)
    void overlappingBranchesActionOrderAutoAndTimeout() {
        var definition = fees("commissionTwoPercent", "commissionOnePercent", false);
        var branches = (ArrayNode) definition.at("/table/transitions/IN_PROGRESS");
        ((ObjectNode) branches.get(0).path("to")).set("conditions", mapper.valueToTree(List.of()));
        ((ObjectNode) branches.get(1).path("to")).set("conditions", mapper.valueToTree(List.of()));
        publish(definition);
        verifyFee("1000.00", "20.00");
        var first = branches.remove(0);
        branches.add(first);
        publish(definition);
        verifyFee("1000.00", "10.00");

        PROBE.clear();
        var auto = mapper.createObjectNode();
        auto.put("initialState", "NEW");
        var table = auto.putObject("table");
        table.put("autoTransitionEnabled", false).put("maxImmediateAutoTransitions", 2);
        var transitions = table.putObject("transitions");
        var destination = transition("NEW", null, "DONE", List.of(),
                List.of("commissionTwoPercent", "commissionOnePercent", "beforeOrderState"), List.of("afterOrderState"));
        ((ObjectNode) destination.path("to")).put("autoTransitionEnabled", true)
                .set("timeout", mapper.valueToTree(Map.of("value", 60, "unit", "MILLISECONDS")));
        transitions.putArray("NEW").add(destination);
        transitions.putArray("DONE");
        publish(auto);
        long start = System.nanoTime();
        var created = create("1.00");
        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(50);
        assertThat(created.path("state").asText()).isEqualTo("DONE");
        assertThat(created.path("commission").decimalValue()).isEqualByComparingTo("0.01");
        assertThat(PROBE).containsExactly("before:NEW:0.01", "after:DONE:0.01");
        transitions.putArray("DONE").add(transition("DONE", null, "NEW", List.of(), List.of(), List.of()));
        table.put("autoTransitionEnabled", true);
        publish(auto);
        var count = jdbc.queryForObject("select count(*) from orders", Long.class);
        post("/api/orders", Map.of("amount", "1.00"), 409);
        assertThat(jdbc.queryForObject("select count(*) from orders", Long.class)).isEqualTo(count);
    }

    @Test @org.junit.jupiter.api.Order(5)
    void actionFailureRollsBackStateAndHistory() {
        var failing = fees("commissionTwoPercent", "commissionOnePercent", true);
        ((ArrayNode) failing.at("/table/transitions/IN_PROGRESS/0/to/postActions")).add("failOrderAction");
        publish(failing);
        var id = create("100.00").path("id").asLong();
        event(id, "SUBMIT");
        var history = get("/api/orders/" + id + "/history");
        post("/api/orders/" + id + "/events", Map.of("event", "FINISH"), 409);
        var unchanged = get("/api/orders/" + id);
        assertThat(unchanged.path("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(unchanged.path("commission").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(get("/api/orders/" + id + "/history")).isEqualTo(history);
    }

    @Test @org.junit.jupiter.api.Order(6)
    void draftsArePhysicallyDeletedAndOnlyHighestNumberIsReused() {
        var definition = fees("commissionTwoPercent", "commissionOnePercent", false);
        int lower = draft(definition);
        int higher = draft(definition);
        delete(lower, 204);
        assertThat(jdbc.queryForObject("select count(*) from fsm_flow_version where flow_key='order' and version=?", Integer.class, lower)).isZero();
        int next = draft(definition);
        assertThat(next).isEqualTo(higher + 1);
        delete(next, 204);
        assertThat(draft(definition)).isEqualTo(next);
        delete(next, 204);
        delete(higher, 204);
        delete(firstVersion, 409);
        assertThat(http.getForEntity("/fsm-admin/api/flows/order/versions/" + higher, JsonNode.class).getStatusCode().value()).isEqualTo(404);
    }

    @Test @org.junit.jupiter.api.Order(7)
    void publicationValidationAndConcurrentDraftsPreserveActiveVersion() {
        var active = get("/fsm-admin/api/flows/order/versions");
        int activeVersion = 0;
        for (var item : active) if (item.path("status").asText().equals("ACTIVE")) activeVersion = item.path("version").asInt();
        var invalid = fees("commissionTwoPercent", "commissionOnePercent", false);
        invalid.putObject("execution").putArray("stateListeners").add("unknownListener");
        int broken = draft(invalid);
        post("/fsm-admin/api/flows/order/versions/" + broken + "/publish", null, 400);
        assertThat(create("100.00").path("flowVersion").asInt()).isEqualTo(activeVersion);
        delete(activeVersion, 409);
        var update = http.exchange("/fsm-admin/api/flows/order/versions/" + activeVersion, HttpMethod.PUT,
                new HttpEntity<>(invalid), JsonNode.class);
        assertThat(update.getStatusCode().value()).isEqualTo(409);
        var definition = fees("commissionTwoPercent", "commissionOnePercent", false);
        var a = CompletableFuture.supplyAsync(() -> draft(definition));
        var b = CompletableFuture.supplyAsync(() -> draft(definition));
        assertThat(a.join()).isNotEqualTo(b.join());
        var behaviors = get("/fsm-admin/api/flows/order/behaviors");
        assertThat(behaviors.findValuesAsText("id")).contains(
                "amountAbove10000", "commissionTwoPercent", "failOrderAction", "beforeOrderState", "afterOrderState");
        for (var behavior : behaviors) {
            if (behavior.path("id").asText().equals("commissionTwoPercent")) {
                assertThat(behavior.path("kind").asText()).isEqualTo("action");
                assertThat(behavior.path("description").asText()).isEqualTo("Set commission to 2% of amount");
            }
        }
    }

    @Test @org.junit.jupiter.api.Order(8)
    void controllersRejectInvalidEventsWithoutChangingDraftsOrOrders() {
        var definition = fees("commissionTwoPercent", "commissionOnePercent", false);
        int version = draft(definition);
        var original = get("/fsm-admin/api/flows/order/versions/" + version);
        int versionCount = get("/fsm-admin/api/flows/order/versions").size();
        var order = create("100.00");
        long id = order.path("id").asLong();
        for (String name : List.of("", "   ", "E".repeat(121), "😀".repeat(61))) {
            ((ObjectNode) definition.path("table").path("transitions").path("NEW").get(0)).put("event", name);
            assertThat(post("/fsm-admin/api/flows/order/versions", definition, 400).path("message").asText())
                    .contains("event must contain 1 to 120 characters");
            var updated = http.exchange("/fsm-admin/api/flows/order/versions/" + version, HttpMethod.PUT,
                    new HttpEntity<>(definition), JsonNode.class);
            assertThat(updated.getStatusCode().value()).isEqualTo(400);
            post("/api/orders/" + id + "/events", Map.of("event", name), 400);
        }
        for (String field : List.of("source", "requestId")) {
            post("/api/orders/" + id + "/events", Map.of("event", "SUBMIT", field, "X".repeat(121)), 400);
        }
        assertThat(get("/fsm-admin/api/flows/order/versions/" + version)).isEqualTo(original);
        assertThat(get("/fsm-admin/api/flows/order/versions").size()).isEqualTo(versionCount);
        assertThat(get("/api/orders/" + id).path("state")).isEqualTo(order.path("state"));
    }

    @Test @org.junit.jupiter.api.Order(9)
    void invalidStoredEventsCannotReplaceActiveFlowAndBoundaryEventsExecute() {
        var definition = fees("commissionTwoPercent", "commissionOnePercent", false);
        int active = create("100.00").path("flowVersion").asInt();
        var activeVersion = get("/fsm-admin/api/flows/order/versions/" + active);
        for (String status : List.of("DRAFT", "ARCHIVED")) {
            int version = draft(definition);
            var invalid = definition.deepCopy();
            ((ObjectNode) invalid.path("table").path("transitions").path("NEW").get(0))
                    .put("event", "E".repeat(121));
            // Simulate a definition stored before event validation existed, in this test's disposable DB.
            jdbc.update("update fsm_flow_version set definition=cast(? as jsonb), status=? where flow_key='order' and version=?",
                    invalid.toString(), status, version);
            post("/fsm-admin/api/flows/order/versions/" + version + (status.equals("DRAFT") ? "/publish" : "/activate"), null, 400);
            assertThat(get("/fsm-admin/api/flows/order/versions/" + version).path("status").asText()).isEqualTo(status);
            assertThat(get("/fsm-admin/api/flows/order/versions/" + active)).isEqualTo(activeVersion);
            assertThat(create("100.00").path("flowVersion").asInt()).isEqualTo(active);
        }
        for (String name : List.of("E".repeat(120), "😀".repeat(60))) {
            ((ObjectNode) definition.path("table").path("transitions").path("NEW").get(0)).put("event", name);
            int version = publish(definition);
            var order = create("100.00");
            assertThat(order.path("flowVersion").asInt()).isEqualTo(version);
            assertThat(event(order.path("id").asLong(), name).path("state").asText()).isEqualTo("IN_PROGRESS");
        }
    }

    private ObjectNode fees(String belowThreshold, String atLeastThreshold, boolean history) {
        var root = mapper.createObjectNode().put("initialState", "NEW");
        var table = root.putObject("table").put("autoTransitionEnabled", false).put("maxImmediateAutoTransitions", 10);
        var transitions = table.putObject("transitions");
        transitions.putArray("NEW").add(transition("NEW", "SUBMIT", "IN_PROGRESS", List.of(), List.of(), List.of()));
        var finish = transitions.putArray("IN_PROGRESS");
        finish.add(transition("IN_PROGRESS", "FINISH", "DONE", List.of("amountBelowCommissionThreshold"), List.of(belowThreshold), List.of("logOrderNotification")));
        finish.add(transition("IN_PROGRESS", "FINISH", "DONE", List.of("amountAtLeastCommissionThreshold"), List.of(atLeastThreshold), List.of("logOrderNotification")));
        transitions.putArray("DONE");
        if (history) {
            var execution = root.putObject("execution");
            execution.putArray("stateListeners").add("recordOrderHistory");
        }
        return root;
    }

    private ObjectNode transition(String from, String event, String to, List<String> guards, List<String> actions, List<String> postActions) {
        var transition = mapper.createObjectNode().put("from", from).put("event", event);
        var target = transition.putObject("to").put("state", to);
        target.set("conditions", mapper.valueToTree(guards));
        target.set("actions", mapper.valueToTree(actions));
        target.set("postActions", mapper.valueToTree(postActions));
        return transition;
    }
    private int draft(JsonNode definition) { return post("/fsm-admin/api/flows/order/versions", definition, 201).path("version").asInt(); }
    private int publish(JsonNode definition) {
        int version = draft(definition);
        post("/fsm-admin/api/flows/order/versions/" + version + "/publish", null, 200);
        return version;
    }
    private void delete(int version, int status) {
        assertThat(http.exchange("/fsm-admin/api/flows/order/versions/" + version, HttpMethod.DELETE, HttpEntity.EMPTY, String.class)
                .getStatusCode().value()).isEqualTo(status);
    }
    private JsonNode create(String amount) {
        return post("/api/orders", Map.of("amount", amount), 201);
    }
    private JsonNode event(long id, String event) { return post("/api/orders/" + id + "/events", Map.of("event", event), 200); }
    private void verifyFee(String amount, String fee) {
        var id = create(amount).path("id").asLong();
        event(id, "SUBMIT");
        var result = event(id, "FINISH");
        assertThat(result.path("commission").decimalValue()).isEqualByComparingTo(fee);
        assertThat(result.path("state").asText()).isEqualTo("DONE");
    }
    private JsonNode get(String path) {
        var result = http.getForEntity(path, JsonNode.class);
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        return Objects.requireNonNull(result.getBody());
    }
    private JsonNode post(String path, Object body, int status) {
        var result = http.postForEntity(path, body, JsonNode.class);
        assertThat(result.getStatusCode().value()).as("%s: %s", path, result.getBody()).isEqualTo(status);
        return Objects.requireNonNull(result.getBody());
    }
}
