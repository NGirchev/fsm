package ru.girchev.fsm.exception

import ru.girchev.fsm.core.BTransition

class DuplicateTransitionException(transition: BTransition<*>) :
    FSMException("Can't add duplicate transition [${transition.from}]->[${transition.to}]")
