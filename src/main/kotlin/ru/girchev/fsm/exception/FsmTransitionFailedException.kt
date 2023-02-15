package ru.girchev.fsm.exception

import java.util.*

class FsmTransitionFailedException(
    source: String,
    target: String,
    domainName: String? = null,
    text: String? = null
) :
    FsmException(
        "Illegal ${domainName?.lowercase(Locale.getDefault()) ?: ""}" +
                "state transition $source->$target" + (text?.map { ", $it" } ?: "")
    )
