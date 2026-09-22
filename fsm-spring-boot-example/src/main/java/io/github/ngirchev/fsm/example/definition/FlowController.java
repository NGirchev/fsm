package io.github.ngirchev.fsm.example.definition;

import io.github.ngirchev.fsm.spring.definition.FlowDefinition;
import io.github.ngirchev.fsm.spring.definition.FlowService;
import io.github.ngirchev.fsm.spring.definition.FlowVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flows/{flowKey}/versions")
@RequiredArgsConstructor
public class FlowController {
    // Receives HTTP requests to create, inspect, edit and publish definition versions.
    private final FlowService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlowVersion create(@PathVariable String flowKey, @RequestBody FlowDefinition definition) {
        return service.createDraft(flowKey, definition);
    }

    @GetMapping
    public List<FlowVersion> list(@PathVariable String flowKey) {
        return service.list(flowKey);
    }

    @GetMapping("/{version}")
    public FlowVersion get(@PathVariable String flowKey, @PathVariable int version) {
        return service.get(flowKey, version);
    }

    @PutMapping("/{version}")
    public FlowVersion update(@PathVariable String flowKey, @PathVariable int version,
                              @RequestBody FlowDefinition definition) {
        return service.updateDraft(flowKey, version, definition);
    }

    @PostMapping("/{version}/publish")
    public FlowVersion publish(@PathVariable String flowKey, @PathVariable int version) {
        return service.publish(flowKey, version);
    }

    @DeleteMapping("/{version}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String flowKey, @PathVariable int version) {
        service.deleteDraft(flowKey, version);
    }
}
