package ru.girchev.fsm

interface TransitionTable<STATE, TRANSITION : Transition<STATE>> {
    val transitions: Map<STATE, LinkedHashSet<out Transition<STATE>>>
    fun getTransitionByState(context: FSMContext<STATE>, newState: STATE): TRANSITION?

    data class To<STATE>(
        val to: STATE,
        val condition: Guard<in FSMContext<STATE>>? = null,
        val action: Action<in FSMContext<STATE>>? = null,
        val timeout: Timeout? = null
    )
}