package ru.girchev.fsm.impl

import ru.girchev.fsm.DomainSupport
import ru.girchev.fsm.FSMContext
import ru.girchev.fsm.impl.basic.BaTransition

abstract class AbstractDomainFSM<DOMAIN : FSMContext<STATE>, STATE, TRANSITION : BaTransition<STATE>, TRANSITION_TABLE : AbstractTransitionTable<STATE, TRANSITION>>(
    internal open val transitionTable: TRANSITION_TABLE
) : DomainSupport<DOMAIN, STATE>