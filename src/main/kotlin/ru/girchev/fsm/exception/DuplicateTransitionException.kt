package ru.girchev.fsm.exception

import ru.girchev.fsm.impl.basic.BTransition

class DuplicateTransitionException(transition: BTransition<*>) :
    FsmException("Can't add duplicate transition [${transition.from}]->[${transition.to.state}]")
