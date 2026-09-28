package io.github.ngirchev.fsm.spring.admin

import io.github.ngirchev.fsm.spring.definition.FlowDefinition
import io.github.ngirchev.fsm.spring.definition.FlowService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping($$"${fsm.admin.base-path:/fsm-admin}")
class FsmAdminController(private val admin: FsmAdminService, private val flows: FlowService,
                         private val securityPresent: Boolean) {
    @GetMapping("")
    fun redirect(request: HttpServletRequest): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.FOUND).header("Location", request.requestURI + "/").build()

    @GetMapping("/", produces = [MediaType.TEXT_HTML_VALUE])
    fun page() = ClassPathResource("fsm-admin/panel/index.html")

    @GetMapping("/api/csrf")
    fun csrf(request: HttpServletRequest): ResponseEntity<Map<String, String>> =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(if (securityPresent) {
            FsmAdminCsrf.read(request)
        } else emptyMap())

    @GetMapping("/api/flows")
    fun registrations() = admin.list()

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
