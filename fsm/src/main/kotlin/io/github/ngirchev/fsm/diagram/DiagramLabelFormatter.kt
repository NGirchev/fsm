package io.github.ngirchev.fsm.diagram

import io.github.ngirchev.fsm.Action
import io.github.ngirchev.fsm.Guard
import io.github.ngirchev.fsm.IdentifiableAction
import io.github.ngirchev.fsm.IdentifiableGuard
import java.security.MessageDigest

internal object DiagramLabelFormatter {

    fun actionLabel(action: Action<*>): String {
        return behaviorLabel(action, "action")
    }

    fun guardLabel(guard: Guard<*>): String {
        return behaviorLabel(guard, "guard")
    }

    private fun behaviorLabel(behavior: Any, fallbackPrefix: String): String {
        val id = when (behavior) {
            is IdentifiableAction<*> -> behavior.id
            is IdentifiableGuard<*> -> behavior.id
            else -> null
        }

        if (!id.isNullOrBlank()) {
            return id
        }

        val text = behavior.toString()
        if (!isDefaultObjectLabel(text, behavior.javaClass.name)) {
            return text
        }

        return "$fallbackPrefix-${classFingerprint(behavior.javaClass)}"
    }

    private fun isDefaultObjectLabel(text: String, className: String): Boolean {
        return text == className || text.startsWith("$className@")
    }

    private fun classFingerprint(clazz: Class<*>): String {
        val bytes = classBytes(clazz) ?: normalizedClassName(clazz).toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(separator = "") { "%02x".format(it) }

        return hash.takeLast(8)
    }

    private fun classBytes(clazz: Class<*>): ByteArray? {
        val resourceName = "${clazz.name.substringAfterLast('.')}.class"
        return clazz.getResourceAsStream(resourceName)?.use { it.readBytes() }
    }

    private fun normalizedClassName(clazz: Class<*>): String {
        return clazz.name.replace(Regex("/0x[0-9a-fA-F]+"), "")
    }
}
