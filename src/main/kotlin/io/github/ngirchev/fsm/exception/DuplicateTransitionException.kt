package io.github.ngirchev.fsm.exception

import io.github.ngirchev.fsm.impl.basic.BTransition

class DuplicateTransitionException(transition: BTransition<*>) :
    FsmException("Can't add duplicate transition [${transition.from}]->[${transition.to.state}]")
