package io.github.ngirchev.fsm.exception

import java.util.*

class FsmTransitionFailedException(
    source: String,
    target: String,
    domainName: String? = null,
    text: String? = null
) :
    FsmException(
        "Illegal ${domainName?.lowercase(Locale.ROOT)?.let { "$it " } ?: ""}" +
                "state transition $source->$target" + (text?.let { ", $it" } ?: "")
    )
