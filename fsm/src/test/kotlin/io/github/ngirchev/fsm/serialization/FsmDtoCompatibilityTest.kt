package io.github.ngirchev.fsm.serialization

import kotlin.test.Test
import kotlin.test.assertEquals

class FsmDtoCompatibilityTest {
    @Test
    fun `old copy JVM signature remains callable and preserves the runtime limit`() {
        val dto = FsmDto(false, emptyMap(), 7)
        val method = FsmDto::class.java.getMethod("copy", Boolean::class.javaPrimitiveType, Map::class.java)

        val copied = method.invoke(dto, true, emptyMap<String, List<TransitionDto>>()) as FsmDto

        assertEquals(dto.copy(autoTransitionEnabled = true), copied)
        assertEquals(7, copied.maxImmediateAutoTransitions)
    }

    @Test
    fun `old Kotlin copy default JVM signature honors its default argument mask`() {
        val transitions = mapOf("NEW" to emptyList<TransitionDto>())
        val dto = FsmDto(false, transitions, 7)
        val method = FsmDto::class.java.getMethod(
            "copy\$default", FsmDto::class.java, Boolean::class.javaPrimitiveType,
            Map::class.java, Int::class.javaPrimitiveType, Any::class.java,
        )

        val changed = method.invoke(null, dto, true, null, 2, null) as FsmDto
        assertEquals(FsmDto(true, transitions, 7), changed)
        val unchanged = method.invoke(null, dto, true, null, 3, null) as FsmDto
        assertEquals(dto, unchanged)
        val replaced = method.invoke(null, dto, true, emptyMap<String, List<TransitionDto>>(), 1, null) as FsmDto
        assertEquals(FsmDto(false, emptyMap(), 7), replaced)
    }
}
