package io.github.ngirchev.fsm.spring.admin

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.web.csrf.CsrfToken

/** Loaded only when Spring Security is present; the starter does not install any security filters. */
internal object FsmAdminCsrf {
    fun read(request: HttpServletRequest): Map<String, String> {
        val token = request.getAttribute(CsrfToken::class.java.name) as CsrfToken?
        return if (token == null) emptyMap() else mapOf("headerName" to token.headerName, "token" to token.token)
    }
}
