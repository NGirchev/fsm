package ru.girchev.fsm.exception

import java.util.*

class FSMEventSourcingTransitionFailedException(
    source: String,
    event: String,
    domainName: String? = null,
    text: String? = null
) :
    FSMException(
        "Illegal ${domainName?.lowercase(Locale.getDefault()) ?: ""}" +
            "state transition for state=[$source] by event=[$event]" + (text?.map { ", $it" } ?: "")
    )
