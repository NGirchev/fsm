package io.github.ngirchev.fsm.spring.admin

import io.github.ngirchev.fsm.spring.definition.FlowDefinition
import java.util.function.Consumer

/** A flow explicitly exposed by the application. Callbacks must not mutate the supplied definition. */
class FsmAdminRegistration @JvmOverloads constructor(
    val flowKey: String,
    val title: String,
    val initialDefinition: FlowDefinition,
    val validateDraft: Consumer<FlowDefinition>,
    val validateActivation: Consumer<FlowDefinition>,
    /** Group shown by the editor; it also preselects the element filter (see [FsmFlowKind]). */
    val kind: String = OTHER_FLOW_KIND,
    val description: String? = null,
    /** Tells the editor whether to offer deletion; [FsmAdminFlows.delete] makes the decision. */
    val deletable: Boolean = false,
)
