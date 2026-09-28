package io.github.ngirchev.fsm.spring.admin

import io.github.ngirchev.fsm.spring.definition.FlowDefinition
import java.util.function.Consumer

/** A flow explicitly exposed by the application. Callbacks must not mutate the supplied definition. */
class FsmAdminRegistration(
    val flowKey: String,
    val title: String,
    val initialDefinition: FlowDefinition,
    val validateDraft: Consumer<FlowDefinition>,
    val validateActivation: Consumer<FlowDefinition>,
)
