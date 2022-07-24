package ru.girchev.fsm.test.document

import ru.girchev.fsm.FSMContext
import java.util.UUID

data class Document(
    val id: String = UUID.randomUUID().toString(),
    override var state: DocumentState = DocumentState.NEW,
    val signRequired: Boolean = false
) : FSMContext<DocumentState>
