package io.github.ngirchev.fsm.spring.admin

import io.github.ngirchev.fsm.spring.definition.FlowDefinition
import io.github.ngirchev.fsm.spring.definition.FlowService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping($$"${fsm.admin.base-path:/fsm-admin}")
class FsmAdminController(private val admin: FsmAdminService, private val flows: FlowService) {
    @GetMapping("/api/flows")
    fun registrations() = admin.list()

    @PostMapping("/api/flows")
    @ResponseStatus(HttpStatus.CREATED)
    fun createFlow(@RequestBody body: NewFlowRequest) =
        admin.createFlow(body.flowKey, body.title, body.kind, body.description, body.copyOf, body.copyVersion)

    @PutMapping("/api/flows/{key}")
    fun renameFlow(@PathVariable key: String, @RequestBody body: Map<String, String?>) =
        admin.renameFlow(key, body["title"], body["description"])

    @DeleteMapping("/api/flows/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteFlow(@PathVariable key: String) = admin.deleteFlow(key)

    @GetMapping("/api/flows/{key}/behaviors")
    fun behaviors(@PathVariable key: String) = admin.behaviors(key)

    @GetMapping("/api/flows/{key}/versions")
    fun list(@PathVariable key: String) = flows.list(admin.registration(key).flowKey)

    @GetMapping("/api/flows/{key}/versions/{version}")
    fun get(@PathVariable key: String, @PathVariable version: Int) = flows.get(admin.registration(key).flowKey, version)

    @PostMapping("/api/flows/{key}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable key: String, @RequestBody(required = false) definition: FlowDefinition?) = admin.create(key, definition)

    @PutMapping("/api/flows/{key}/versions/{version}")
    fun update(@PathVariable key: String, @PathVariable version: Int, @RequestBody definition: FlowDefinition) =
        admin.update(key, version, definition)

    @PostMapping("/api/flows/{key}/versions/{version}/publish")
    fun publish(@PathVariable key: String, @PathVariable version: Int) = admin.activate(key, version, true)

    @PostMapping("/api/flows/{key}/versions/{version}/activate")
    fun activate(@PathVariable key: String, @PathVariable version: Int) = admin.activate(key, version, false)

    @DeleteMapping("/api/flows/{key}/versions/{version}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable key: String, @PathVariable version: Int) = flows.deleteDraft(admin.registration(key).flowKey, version)
}

/** Body of `POST /api/flows`; [copyVersion] picks the version of [copyOf] to copy. */
data class NewFlowRequest(
    val flowKey: String? = null,
    val title: String? = null,
    val kind: String? = null,
    val description: String? = null,
    val copyOf: String? = null,
    val copyVersion: Int? = null,
)
