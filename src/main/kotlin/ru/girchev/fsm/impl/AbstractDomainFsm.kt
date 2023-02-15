package ru.girchev.fsm.impl

import ru.girchev.fsm.DomainSupport
import ru.girchev.fsm.StateContext

abstract class AbstractDomainFsm<DOMAIN : StateContext<STATE>, STATE, TRANSITION : AbstractTransition<STATE>, TRANSITION_TABLE : AbstractTransitionTable<STATE, TRANSITION>>(
    internal open val transitionTable: TRANSITION_TABLE
) : DomainSupport<DOMAIN, STATE>