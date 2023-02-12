package ru.girchev.fsm.exception

import ru.girchev.fsm.impl.basic.BaTransition

class DuplicateTransitionException(transition: BaTransition<*>) :
    FSMException("Can't add duplicate transition [${transition.from}]->[${transition.to}]")
