package io.github.ngirchev.fsm.example.flow

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

data class FlowValidationResponse(val valid: Boolean, val issues: List<FlowValidationIssue>)

@RestController
@RequestMapping("/api/flows/{flowKey}/versions")
class FlowController(private val service: FlowService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable flowKey: String, @RequestBody definition: FlowDefinition): FlowVersion =
        service.createDraft(flowKey, definition)

    @GetMapping
    fun list(@PathVariable flowKey: String): List<FlowVersion> = service.list(flowKey)

    @GetMapping("/{version}")
    fun get(@PathVariable flowKey: String, @PathVariable version: Int): FlowVersion = service.get(flowKey, version)

    @PutMapping("/{version}")
    fun update(
        @PathVariable flowKey: String,
        @PathVariable version: Int,
        @RequestBody definition: FlowDefinition,
    ): FlowVersion = service.updateDraft(flowKey, version, definition)

    @PostMapping("/{version}/validate")
    fun validate(@PathVariable flowKey: String, @PathVariable version: Int): FlowValidationResponse {
        val issues = service.validate(service.get(flowKey, version).definition)
        return FlowValidationResponse(issues.isEmpty(), issues)
    }

    @PostMapping("/{version}/publish")
    fun publish(@PathVariable flowKey: String, @PathVariable version: Int): FlowVersion =
        service.publish(flowKey, version)
}
