package ru.girchev.fsm.impl.basic

import ru.girchev.fsm.*
import ru.girchev.fsm.impl.AbstractTransition

open class BTransition<STATE> : AbstractTransition<STATE> {
    constructor(from: STATE, to: STATE) : super(from, To(to))
    constructor(from: STATE, to: To<STATE>) : super(from, to)
}
