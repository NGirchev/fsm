package io.github.ngirchev.fsm.impl

import io.github.ngirchev.fsm.DomainSupport
import io.github.ngirchev.fsm.StateContext

abstract class AbstractDomainFsm<DOMAIN : StateContext<STATE>, STATE, TRANSITION : AbstractTransition<STATE>, TRANSITION_TABLE : AbstractTransitionTable<STATE, TRANSITION>>(
    open val transitionTable: TRANSITION_TABLE
) : DomainSupport<DOMAIN, STATE>