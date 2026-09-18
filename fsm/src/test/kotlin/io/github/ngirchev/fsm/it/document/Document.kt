package io.github.ngirchev.fsm.it.document

import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.Transition
import java.util.UUID

data class Document(
    val id: String = UUID.randomUUID().toString(),
    override var state: DocumentState = DocumentState.NEW,
    val signRequired: Boolean = false,
    override var currentTransition: Transition<DocumentState>? = null
) : StateContext<DocumentState>
