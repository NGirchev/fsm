package io.github.ngirchev.fsm.example.flow

import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.boot.ApplicationArguments
import io.github.ngirchev.fsm.serialization.FsmDto
import io.github.ngirchev.fsm.serialization.TransitionDto
import io.github.ngirchev.fsm.serialization.ToDto
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ActiveFlowStartupValidatorTest {
    @Test
    fun `fails startup when an active definition enables automatic execution without a limit`() {
        val repository = mock(FlowRepository::class.java)
        val loader = FlowLoader(emptyMap(), emptyMap())
        val definition = FlowDefinition("RED", FsmDto(true, mapOf(
            "RED" to listOf(TransitionDto("RED", ToDto("RED", emptyList(), emptyList(), emptyList(), null), null)),
        )))
        val now = OffsetDateTime.now()
        `when`(repository.activeVersions()).thenReturn(
            listOf(FlowVersion("traffic-light", 1, FlowVersionStatus.ACTIVE, definition, now, now, now)),
        )

        val validator = ActiveFlowStartupValidator(repository, loader)

        assertFailsWith<InvalidFlowDefinitionException> {
            validator.run(mock(ApplicationArguments::class.java))
        }
    }
}
