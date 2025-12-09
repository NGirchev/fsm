package io.github.ngirchev.fsm.exception

import java.util.*

class FsmEventSourcingTransitionFailedException(
    source: String,
    event: String,
    domainName: String? = null,
    text: String? = null
) :
    FsmException(
        "Illegal ${domainName?.lowercase(Locale.getDefault()) ?: ""}" +
            "state transition for state=[$source] by event=[$event]" + (text?.map { ", $it" } ?: "")
    )
