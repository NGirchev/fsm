package io.github.ngirchev.fsm.example.flow

import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.boot.ApplicationArguments
import org.springframework.context.support.GenericApplicationContext
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ActiveFlowStartupValidatorTest {
    @Test
    fun `fails startup when an active definition contains a disallowed auto transition cycle`() {
        val repository = mock(FlowRepository::class.java)
        val compiler = FlowCompiler(FlowBehaviorRegistry(GenericApplicationContext().apply { refresh() }))
        val definition = FlowDefinition(
            schemaVersion = 1,
            initialState = "RED",
            autoTransitionEnabled = true,
            states = listOf("RED", "GREEN"),
            events = emptyList(),
            transitions = listOf(
                FlowTransitionDefinition("red-green", "RED", "GREEN", FlowTriggerDefinition("auto")),
                FlowTransitionDefinition("green-red", "GREEN", "RED", FlowTriggerDefinition("auto")),
            ),
        )
        val now = OffsetDateTime.now()
        `when`(repository.activeVersions()).thenReturn(
            listOf(FlowVersion("traffic-light", 1, FlowVersionStatus.ACTIVE, definition, now, now, now)),
        )

        val validator = ActiveFlowStartupValidator(repository, compiler)

        assertFailsWith<InvalidFlowDefinitionException> {
            validator.run(mock(ApplicationArguments::class.java))
        }
    }
}
