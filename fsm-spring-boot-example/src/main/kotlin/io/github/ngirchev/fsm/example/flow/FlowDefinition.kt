package io.github.ngirchev.fsm.example.flow

import io.github.ngirchev.fsm.serialization.FsmDto

data class FlowDefinition(
    val initialState: String,
    val table: FsmDto,
)

data class FlowValidationIssue(val path: String, val message: String)

class InvalidFlowDefinitionException(val issues: List<FlowValidationIssue>) :
    IllegalArgumentException("Invalid FSM flow definition")
