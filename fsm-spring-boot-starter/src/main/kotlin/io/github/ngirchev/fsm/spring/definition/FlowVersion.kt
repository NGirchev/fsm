package io.github.ngirchev.fsm.spring.definition

@JvmRecord
data class FlowVersion(
    val flowKey: String,
    val version: Int,
    val status: FlowVersionStatus,
    val definition: FlowDefinition,
)
