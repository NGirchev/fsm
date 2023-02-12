package ru.girchev.fsm.impl.basic

import ru.girchev.fsm.FSMContext
import ru.girchev.fsm.Action
import ru.girchev.fsm.Guard
import ru.girchev.fsm.Timeout
import ru.girchev.fsm.impl.AbstractTransition

open class BaTransition<STATE>(
    from: STATE,
    to: STATE,
    override val condition: Guard<in FSMContext<STATE>>? = null,
    override val action: Action<in FSMContext<STATE>>? = null,
    override val timeout: Timeout? = null
) : AbstractTransition<STATE>(from, to, condition, action, timeout)
