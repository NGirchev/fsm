package io.github.ngirchev.fsm

interface StateContext<STATE> {
    var state: STATE
    var currentTransition: Transition<STATE>?
}
