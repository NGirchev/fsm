package io.github.ngirchev.fsm.impl.basic

import io.github.ngirchev.fsm.*
import io.github.ngirchev.fsm.impl.AbstractTransition

open class BTransition<STATE> : AbstractTransition<STATE> {
    constructor(from: STATE, to: STATE) : super(from, To(to))
    constructor(from: STATE, to: To<STATE>) : super(from, to)
}
