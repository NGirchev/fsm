package io.github.ngirchev.fsm.exception

class AutoTransitionLimitExceededException(
    val limit: Int,
) : FsmException("Immediate auto-transition limit of $limit was exceeded")
