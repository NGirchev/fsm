package io.github.ngirchev.fsm

fun interface AutoTransitionScheduler<STATE> {
    fun schedule(
        context: StateContext<STATE>,
        transition: Transition<STATE>,
        runTransition: () -> Unit,
    )
}

class ImmediateAutoTransitionScheduler<STATE> : AutoTransitionScheduler<STATE> {
    override fun schedule(
        context: StateContext<STATE>,
        transition: Transition<STATE>,
        runTransition: () -> Unit,
    ) {
        runTransition()
    }
}
