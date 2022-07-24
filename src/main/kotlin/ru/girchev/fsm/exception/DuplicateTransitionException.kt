package ru.girchev.fsm.exception

import ru.girchev.fsm.core.SimpleTransition

class DuplicateTransitionException(transition: SimpleTransition<*>) :
    FSMException("Can't add duplicate transition [${transition.from}]->[${transition.to}]")
