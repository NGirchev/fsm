package io.github.ngirchev.fsm

import io.github.ngirchev.fsm.diagram.DiagramLabelFormatter
import io.github.ngirchev.fsm.exception.FsmEventSourcingTransitionFailedException
import io.github.ngirchev.fsm.exception.FsmTransitionFailedException
import io.github.ngirchev.fsm.exception.FsmException
import io.github.ngirchev.fsm.impl.AbstractDomainFsm
import io.github.ngirchev.fsm.impl.AbstractFsm
import io.github.ngirchev.fsm.impl.AbstractTransitionTable
import io.github.ngirchev.fsm.impl.basic.BDomainFsm
import io.github.ngirchev.fsm.impl.basic.BTransition
import io.github.ngirchev.fsm.impl.basic.BTransitionTable
import io.github.ngirchev.fsm.impl.extended.ExDomainFsm
import io.github.ngirchev.fsm.impl.extended.ExTransition
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import io.github.ngirchev.fsm.impl.extended.EventToMultipleBuilder
import io.github.ngirchev.fsm.impl.extended.ToBuilder
import io.github.ngirchev.fsm.impl.extended.ToMultipleBuilder
import io.github.ngirchev.fsm.serialization.FsmDto
import io.github.ngirchev.fsm.serialization.ToDto
import io.github.ngirchev.fsm.serialization.toDto
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import kotlin.test.*

class CompatibilityCoverageTest {
    private class Context(override var state: String) : StateContext<String> {
        override var currentTransition: Transition<String>? = null
    }
    private class LegacyTable : TransitionTable<String, BTransition<String>> {
        override val transitions = emptyMap<String, LinkedHashSet<BTransition<String>>>()
        override fun getTransitionByState(context: StateContext<String>, newState: String): BTransition<String>? = null
        override fun getAutoTransition(context: StateContext<String>): BTransition<String>? = null
        override fun createFsm(initialState: String) = BTransitionTable.Builder<String>().build().createFsm(initialState)
        override fun <DOMAIN : StateContext<String>> createDomainFsm(): DomainSupport<DOMAIN, String> =
            BTransitionTable.Builder<String>().build().createDomainFsm()
    }
    private class RawTable : AbstractTransitionTable<String, BTransition<String>>(emptyMap(), false) {
        override fun createFsm(initialState: String) = RawFsm(initialState, this)
        override fun <DOMAIN : StateContext<String>> createDomainFsm(): DomainSupport<DOMAIN, String> =
            object : AbstractDomainFsm<DOMAIN, String, BTransition<String>, RawTable>(this) {
                override fun changeState(domain: DOMAIN, newState: String) { domain.state = newState }
            }
    }
    private class RawFsm : AbstractFsm<String, BTransition<String>, RawTable> {
        constructor(state: String, table: RawTable) : super(state, table)
        constructor(context: StateContext<String>, table: RawTable) : super(context, table)
        fun scheduler() = autoTransitionScheduler
    }
    private class ExposedBasicTable : BTransitionTable<String>(emptyMap(), false, ImmediateAutoTransitionScheduler()) {
        fun scheduler() = autoTransitionScheduler
    }
    private class ExposedExtendedTable : ExTransitionTable<String, String>(emptyMap(), autoTransitionScheduler = ImmediateAutoTransitionScheduler()) {
        fun scheduler() = autoTransitionScheduler
    }
    private class ExposedBasicDomain(table: BTransitionTable<String>) : BDomainFsm<Context, String>(table) {
        fun auto() = autoTransitionEnabled
    }
    private class ExposedExtendedDomain(table: ExTransitionTable<String, String>) : ExDomainFsm<Context, String, String>(table) {
        fun auto() = autoTransitionEnabled
    }

    @Test fun `abstract extension constructors preserve default state and scheduler`() {
        val table = RawTable()
        assertNull(table.getAutoTransition(Context("NEW")))
        assertNull(table.getAutoTransition(Context("NEW"), false))
        table.autoTransitionEnabled = true
        assertTrue(table.autoTransitionEnabled)
        for (fsm in listOf(RawFsm("NEW", table), RawFsm(Context("NEW"), table))) {
            assertEquals("NEW", fsm.getState())
            assertTrue(fsm.scheduler().runsSynchronously)
        }
        assertSame(table, table.createDomainFsm<Context>().let {
            (it as AbstractDomainFsm<*, *, *, *>).transitionTable
        })
        assertTrue(ExposedBasicTable().scheduler().runsSynchronously)
        assertTrue(ExposedExtendedTable().scheduler().runsSynchronously)
        assertNull(ExposedBasicDomain(BTransitionTable.Builder<String>().build()).auto())
        assertNull(ExposedExtendedDomain(ExTransitionTable.Builder<String, String>().build()).auto())
        val scheduler = ImmediateAutoTransitionScheduler<String>()
        assertTrue(To("DONE", emptyList(), emptyList(), emptyList(), autoTransitionEnabled = true).autoTransitionEnabled)
        assertTrue(To("DONE", emptyList(), emptyList(), emptyList(), autoTransitionScheduler = scheduler).autoTransitionEnabled)
        val builder = ExTransitionTable.Builder<String, String>()
        builder.add("NEW", to = arrayOf(To("DONE")))
        assertEquals("DONE", builder.build().transitions.getValue("NEW").single().to.state)
        ToBuilder("OTHER", "TARGET", builder).end()
        EventToMultipleBuilder(ToMultipleBuilder("EVENT", builder), "GO")
            .to("FINISH").end()
        EventToMultipleBuilder(ToMultipleBuilder("EMPTY", builder), "GO").endMultiple()
        assertNull(ExTransition<String, String>("NEW", To("DONE")).event)
    }

    @Test fun `legacy DefaultImpls entry points remain callable by Java clients`() {
        val table = LegacyTable()
        val context = Context("NEW")
        val defaults = Class.forName("io.github.ngirchev.fsm.TransitionTable\$DefaultImpls")
        assertEquals(false, defaults.getMethod("getAutoTransitionEnabled", TransitionTable::class.java).invoke(null, table))
        assertNull(defaults.getMethod("getAutoTransition", TransitionTable::class.java, StateContext::class.java).invoke(null, table, context))
        assertNull(defaults.getMethod("getAutoTransition", TransitionTable::class.java, StateContext::class.java, Boolean::class.javaPrimitiveType).invoke(null, table, context, true))
        assertNull(defaults.getMethod("getAutoTransition\$default", TransitionTable::class.java, StateContext::class.java,
            Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType, Any::class.java).invoke(null, table, context, true, 2, null))
        val named = object : IdentifiableAutoTransitionScheduler<String> {
            override val id = "external"
            override fun schedule(context: StateContext<String>, transition: Transition<String>, runTransition: () -> Unit) { }
        }
        assertFalse(named.runsSynchronously)
        val baseDefaults = Class.forName("io.github.ngirchev.fsm.AutoTransitionScheduler\$DefaultImpls")
        assertEquals(false, baseDefaults.getMethod("getRunsSynchronously", AutoTransitionScheduler::class.java).invoke(null, named))
        val namedDefaults = Class.forName("io.github.ngirchev.fsm.IdentifiableAutoTransitionScheduler\$DefaultImpls")
        assertEquals(false, namedDefaults.getMethod("getRunsSynchronously", IdentifiableAutoTransitionScheduler::class.java).invoke(null, named))
    }

    @Test fun `legacy copy rejects a missing required map and default constructors retain defaults`() {
        val dto = FsmDto(false, emptyMap(), 7)
        val copy = FsmDto::class.java.getMethod("copy\$default", FsmDto::class.java, Boolean::class.javaPrimitiveType,
            Map::class.java, Int::class.javaPrimitiveType, Any::class.java)
        val exception = assertFailsWith<InvocationTargetException> { copy.invoke(null, dto, false, null, 0, null) }
        assertIs<IllegalArgumentException>(exception.cause)
        val constructor = FsmDto::class.java.declaredConstructors.single { it.parameterCount == 5 }
        val defaultDto = constructor.newInstance(false, emptyMap<String, Any>(), 99, 4, null) as FsmDto
        assertEquals(0, defaultDto.maxImmediateAutoTransitions)
        assertTrue(ToDto("DONE", emptyList(), emptyList(), emptyList(), null, "queued").autoTransitionEnabled)
        val scheduler = object : IdentifiableAutoTransitionScheduler<String> {
            override val id: String? = null
            override fun schedule(context: StateContext<String>, transition: Transition<String>, runTransition: () -> Unit) { }
        }
        val table = ExTransitionTable.Builder<String, String>()
            .add("NEW", to = arrayOf(To("DONE", autoTransitionScheduler = scheduler))).build()
        assertFailsWith<FsmException> { table.toDto() }
    }

    @Test fun `diagram labels retain custom text and fingerprint resource-less behaviors`() {
        val human = object : Action<Any> {
            override fun invoke(context: Any) { }
            override fun toString() = "human label"
        }
        assertEquals("human label", DiagramLabelFormatter.actionLabel(human))
        val blank = object : IdentifiableGuard<Any> {
            override val id = " "
            override fun invoke(context: Any) = true
            override fun toString() = javaClass.name
        }
        assertTrue(DiagramLabelFormatter.guardLabel(blank).startsWith("guard-"))
        val nullId = object : IdentifiableAction<Any> {
            override val id: String? = null
            override fun invoke(context: Any) { }
        }
        assertTrue(DiagramLabelFormatter.actionLabel(nullId).startsWith("action-"))
        val proxy = Proxy.newProxyInstance(Action::class.java.classLoader, arrayOf(Action::class.java)) { instance, method, _ ->
            if (method.name == "toString") instance.javaClass.name else null
        } as Action<*>
        val label = DiagramLabelFormatter.actionLabel(proxy)
        assertTrue(label.matches(Regex("action-[0-9a-f]{8}")))
        assertEquals(label, DiagramLabelFormatter.actionLabel(proxy))
    }

    @Test fun `optional exception details are independent`() {
        assertEquals("Illegal order state transition a->b", FsmTransitionFailedException("a", "b", "ORDER").message)
        assertEquals("Illegal state transition a->b, detail", FsmTransitionFailedException("a", "b", text = "detail").message)
        assertEquals("Illegal order state transition for state=[a] by event=[go]", FsmEventSourcingTransitionFailedException("a", "go", "ORDER").message)
        assertEquals("Illegal state transition for state=[a] by event=[go], detail", FsmEventSourcingTransitionFailedException("a", "go", text = "detail").message)
    }

    @Test fun `reflection bridge reports access denied by a closed JDK module`() {
        val clone = Any::class.java.getDeclaredMethod("clone")
        assertFalse(clone.trySetAccessible())
        val bridge = Class.forName("io.github.ngirchev.fsm.TransitionTableKt").declaredMethods
            .single { it.name == "invokeAutoTransitionOverride" }
        bridge.isAccessible = true
        val error = assertFailsWith<InvocationTargetException> {
            bridge.invoke(null, Any(), clone, emptyArray<Any>())
        }
        assertIs<IllegalAccessException>(error.cause)
        assertTrue(error.cause!!.message!!.contains("Cannot access auto transition override"))
    }
}
