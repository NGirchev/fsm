package io.github.ngirchev.fsm.example.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import io.github.ngirchev.fsm.serialization.TransitionDto;
import io.github.ngirchev.fsm.spring.definition.FlowDefinition;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FlowDefinitionJsonTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new KotlinModule.Builder().build());

    @Test
    void seedUsesCoreFormatAndPreservesTransitionOrder() throws Exception {
        String json = seedJson();
        var original = mapper.readTree(json);
        var definition = mapper.readValue(json, FlowDefinition.class);
        assertThat(definition.initialState()).isEqualTo("NEW");
        assertThat(definition.table().getTransitions().get("NEW"))
                .extracting(TransitionDto::getEvent).containsExactly("SUBMIT");
        assertThat(definition.table().getTransitions().get("IN_PROGRESS"))
                .extracting(TransitionDto::getEvent).containsExactly("FINISH");
        assertThat(definition.table().getTransitions().get("COMPLETED")).isEmpty();
        assertThat(mapper.readTree(json)).isEqualTo(original);
    }

    @Test
    void visualMetadataRoundTripsWithoutChangingTheExecutableTable() throws Exception {
        var original = mapper.readValue(seedJson(), FlowDefinition.class);
        assertThat(original.editor()).isNull();
        var layout = mapper.readTree("""
                {"name":"Order", "states":[{"id":"new","label":"NEW",
                "position":{"x":-214.75,"y":870.25}}]}
                """);
        var definition = new FlowDefinition(original.initialState(), original.table(), layout);
        var restored = mapper.readValue(mapper.writeValueAsString(definition), FlowDefinition.class);
        assertThat(restored).isEqualTo(definition);
        assertThat(restored.table()).isEqualTo(original.table());
        assertThat(restored.editor().at("/states/0/position/x").asDouble()).isEqualTo(-214.75);
    }

    @Test
    void newDefinitionsRoundTripUsingCoreTableFormat() throws Exception {
        var definition = mapper.readValue(seedJson(), FlowDefinition.class);
        String json = mapper.writeValueAsString(definition);
        assertThat(mapper.readTree(json).has("schemaVersion")).isFalse();
        assertThat(mapper.readValue(json, FlowDefinition.class)).isEqualTo(definition);
    }

    private String seedJson() throws Exception {
        try (var input = getClass().getResourceAsStream("/db/migration/V2__seed_order_flow.sql")) {
            String seed = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int start = seed.indexOf("$json$") + "$json$".length();
            return seed.substring(start, seed.indexOf("$json$", start));
        }
    }
}
