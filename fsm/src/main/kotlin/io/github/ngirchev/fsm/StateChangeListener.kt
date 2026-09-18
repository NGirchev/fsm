package io.github.ngirchev.fsm

/**
 * Listener for FSM state change notifications
 */
fun interface StateChangeListener<STATE> {
    /**
     * Called when state changes
     * @param context state context
     * @param oldState previous state
     * @param newState new state
     */
    fun onStateChanged(context: StateContext<STATE>, oldState: STATE, newState: STATE)
}
