package ru.girchev.fsm.it.document

import ru.girchev.fsm.StateContext
import ru.girchev.fsm.Transition
import java.util.UUID

data class Document(
    val id: String = UUID.randomUUID().toString(),
    override var state: DocumentState = DocumentState.NEW,
    val signRequired: Boolean = false,
    override var currentTransition: Transition<DocumentState>? = null
) : StateContext<DocumentState>
