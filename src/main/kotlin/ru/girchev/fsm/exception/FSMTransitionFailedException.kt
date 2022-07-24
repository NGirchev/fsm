package ru.girchev.fsm.exception

import java.util.*

class FSMTransitionFailedException(
    source: String,
    target: String,
    domainName: String? = null,
    text: String? = null
) :
    FSMException(
        "Illegal ${domainName?.lowercase(Locale.getDefault()) ?: ""}" +
                "state transition $source->$target" + (text?.map { ", $it" } ?: "")
    )
