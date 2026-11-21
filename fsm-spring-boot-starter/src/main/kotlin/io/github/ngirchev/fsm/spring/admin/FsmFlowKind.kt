package io.github.ngirchev.fsm.spring.admin

/** Kind reported for beans and flows that declare none. */
internal const val OTHER_FLOW_KIND = "other"

/**
 * Kind of flow whose context a guard, action or listener bean works with, for example `@FsmFlowKind("agent")`.
 * The administration API returns it with every catalog entry so an editor can filter by it; unmarked beans are
 * reported as `other`. It does not restrict which flows may use the bean.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class FsmFlowKind(val value: String)
