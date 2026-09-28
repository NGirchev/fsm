package io.github.ngirchev.fsm.example.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import io.github.ngirchev.fsm.serialization.TransitionDto;
import io.github.ngirchev.fsm.spring.definition.FlowDefinition;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class FlowDefinitionJsonTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new KotlinModule.Builder().build());

    @Test
    void seedUsesCoreFormatAndPreservesTransitionOrder() throws Exception {
        String json = seedJson();
        var original = mapper.readTree(json);
        var definition = mapper.readValue(json, FlowDefinition.class);
        assertThat(definition.initialState()).isEqualTo("NEW");
        assertThat(definition.table().getMaxImmediateAutoTransitions()).isZero();
        assertThat(definition.table().getTransitions().get("NEW"))
                .extracting(TransitionDto::getEvent).containsExactly("SUBMIT");
        assertThat(definition.table().getTransitions().get("IN_PROGRESS"))
                .extracting(TransitionDto::getEvent).containsExactly("FINISH", "FINISH");
        assertThat(definition.table().getTransitions().get("IN_PROGRESS"))
                .extracting(transition -> transition.getTo().getState())
                .containsExactly("COMMISSION_2_PERCENT", "COMMISSION_1_PERCENT");
        assertThat(definition.table().getTransitions().get("COMMISSION_2_PERCENT"))
                .extracting(TransitionDto::getEvent).containsExactly((String) null);
        assertThat(definition.table().getTransitions().get("COMMISSION_1_PERCENT"))
                .extracting(TransitionDto::getEvent).containsExactly((String) null);
        assertThat(definition.table().getTransitions().get("COMPLETED")).isEmpty();
        assertThat(mapper.readTree(json)).isEqualTo(original);
    }

    @Test
    void visualMetadataRoundTripsWithoutChangingTheExecutableTable() throws Exception {
        var original = mapper.readValue(seedJson(), FlowDefinition.class);
        assertThat(java.util.Objects.requireNonNull(original.editor()).at("/states/0/color").asText()).isEqualTo("#6d28d9");
        var layout = mapper.readTree("""
                {"name":"Order", "states":[{"id":"new","label":"NEW",
                "position":{"x":-214.75,"y":870.25}}]}
                """);
        var definition = new FlowDefinition(original.initialState(), original.table(), layout);
        var restored = mapper.readValue(mapper.writeValueAsString(definition), FlowDefinition.class);
        assertThat(restored).isEqualTo(definition);
        assertThat(restored.table()).isEqualTo(original.table());
        assertThat(Objects.requireNonNull(restored.editor()).at("/states/0/position/x").asDouble()).isEqualTo(-214.75);
    }

    @Test
    void newDefinitionsRoundTripUsingCoreTableFormat() throws Exception {
        var definition = mapper.readValue(seedJson(), FlowDefinition.class);
        String json = mapper.writeValueAsString(definition);
        assertThat(mapper.readTree(json).has("schemaVersion")).isFalse();
        assertThat(mapper.readValue(json, FlowDefinition.class)).isEqualTo(definition);
    }

    private String seedJson() throws Exception {
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/V1__create_fsm_tables.sql"))) {
            String seed = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int start = seed.indexOf("$json$") + "$json$".length();
            return seed.substring(start, seed.indexOf("$json$", start));
        }
    }
}
