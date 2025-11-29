package ru.girchev.fsm

interface StateContext<STATE> {
    var state: STATE
    var currentTransition: Transition<STATE>?
}
