package io.github.ngirchev.fsm.example.flow

import io.github.ngirchev.fsm.StateContext
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

fun interface DynamicGuard {
    fun test(context: StateContext<String>): Boolean
}

fun interface DynamicAction {
    fun execute(context: StateContext<String>)
}

@Component
class FlowBehaviorRegistry(private val applicationContext: ApplicationContext) {
    fun hasGuard(name: String): Boolean = applicationContext.containsBean(name) &&
        applicationContext.isTypeMatch(name, DynamicGuard::class.java)

    fun hasAction(name: String): Boolean = applicationContext.containsBean(name) &&
        applicationContext.isTypeMatch(name, DynamicAction::class.java)

    fun guard(name: String): DynamicGuard = applicationContext.getBean(name, DynamicGuard::class.java)

    fun action(name: String): DynamicAction = applicationContext.getBean(name, DynamicAction::class.java)
}
