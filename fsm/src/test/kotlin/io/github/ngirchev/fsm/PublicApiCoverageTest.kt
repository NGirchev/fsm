package io.github.ngirchev.fsm

import io.github.ngirchev.fsm.diagram.*
import io.github.ngirchev.fsm.exception.*
import io.github.ngirchev.fsm.impl.basic.*
import io.github.ngirchev.fsm.impl.extended.*
import io.github.ngirchev.fsm.serialization.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PublicApiCoverageTest {
    @TempDir lateinit var directory: Path
    private class Context(override var state: String) : StateContext<String> {
        override var currentTransition: Transition<String>? = null
    }

    @Test fun `demonstrations generate complete examples`() {
        val files = (1..2).flatMap { n ->
            listOf("json", "mermaid", "plantuml").map { Path.of("simple_order_fsm$n.$it") }
        }
        val previous = files.associateWith { if (Files.exists(it)) Files.readAllBytes(it) else null }
        try {
            val output = capture {
                FsmDiagramDemo.main(emptyArray())
                FsmSerializationDemo.main(emptyArray())
            }
            assertTrue(output.contains("NEW"))
            assertTrue(output.contains("SHIPPED"))
            assertTrue(output.contains("stateDiagram-v2"))
            assertTrue(files.all { Files.size(it) > 0 })
        } finally {
            previous.forEach { (path, bytes) ->
                if (bytes == null) Files.deleteIfExists(path) else Files.write(path, bytes)
            }
        }
    }

    @Test fun `basic single and multiple auto DSL preserve callbacks and scheduling`() {
        val calls = mutableListOf<String>()
        val callbacks = mutableListOf<() -> Unit>()
        val scheduler = AutoTransitionScheduler<String> { _, _, callback -> callbacks.add(callback) }
        val table = BTransitionTable.Builder<String>()
            .from("NEW").to("READY").auto()
            .condition { true }.action { calls.add("action") }.postAction { calls.add("post") }
            .deferWith(scheduler).end()
            .from("READY").toMultiple().to("DONE").auto()
            .condition { true }.action { calls.add("next") }.postAction { calls.add("last") }
            .deferWith(scheduler).end().endMultiple().build()
        val fsm = BFsm(Context("NEW"), table, autoTransitionScheduler = scheduler)
        fsm.startAutoTransitions()
        assertEquals("NEW", fsm.getState())
        callbacks.removeAt(0).invoke()
        callbacks.removeAt(0).invoke()
        assertEquals("DONE", fsm.getState())
        assertEquals(listOf("action", "post", "next", "last"), calls)
        val domain = Context("NEW")
        val domainFsm = BDomainFsm<Context, String>(table, autoTransitionScheduler = scheduler)
        assertSame(table, domainFsm.transitionTable)
        domainFsm.changeState(domain, "READY")
        callbacks.removeAt(0).invoke()
        assertEquals("DONE", domain.state)
        assertFailsWith<DuplicateTransitionException> { BTransitionTable.Builder<String>().add("a", "b", "b") }
    }

    @Test fun `extended event and auto wrappers preserve all transition handlers`() {
        val calls = mutableListOf<String>()
        val zero = Timeout(0)
        val scheduler = ImmediateAutoTransitionScheduler<String>()
        val builder = ExTransitionTable.Builder<String, String>()
        EventFromBuilder("NEW", builder, "GO").to("READY")
            .onCondition { true }.action { calls.add("event") }.postAction { calls.add("event-post") }
            .timeout(zero).end()
            .from("READY").to("NEXT").auto().onCondition { true }
            .action { calls.add("auto") }.postAction { calls.add("auto-post") }.timeout(zero).end()
            .from("NEXT").toMultiple().to("DONE").auto().onCondition { true }
            .action { calls.add("multi") }.postAction { calls.add("multi-post") }.timeout(zero).end()
            .endMultiple()
        val table = builder.build()
        val fsm = ExFsm("NEW", table, scheduler)
        fsm.onEvent("GO")
        assertEquals("DONE", fsm.getState())
        assertEquals(listOf("event", "event-post", "auto", "auto-post", "multi", "multi-post"), calls)
        val context = Context("NEW")
        ExFsm(context, table, scheduler).onEvent("GO")
        assertEquals("DONE", context.state)
        val domainFsm = ExDomainFsm<Context, String, String>(table, autoTransitionScheduler = scheduler)
        assertSame(table, domainFsm.transitionTable)
        assertEquals("NEW", domainFsm.getFsmForDomain(Context("NEW")).getState())
        val duplicate = ExTransitionTable.Builder<String, String>().add("a", "go", "b")
        assertFailsWith<DuplicateTransitionException> { duplicate.add("a", "go", "b") }
        val multiple = io.github.ngirchev.fsm.impl.extended.ToMultipleTransitionBuilder("a", "b", io.github.ngirchev.fsm.impl.extended.ToMultipleBuilder("a", builder))
        multiple.onEvent("first")
        assertFailsWith<FsmException> { multiple.onEvent("second") }
        assertFailsWith<FsmException> {
            builder.add("a", "event", "b", autoTransitionEnabled = true)
        }
    }

    @Test fun `listener failures do not interrupt transitions or other listeners`() {
        val table = BTransitionTable.Builder<String>().add("NEW", "DONE").build()
        val fsm = table.createFsm("NEW")
        var notifications = 0
        fsm.addStateChangeListener { _, _, _ -> error("listener") }
        fsm.addStateChangeListener { _, _, _ -> notifications++ }
        fsm.addAutoTransitionCompletionListener { error("completion") }
        fsm.addAutoTransitionCompletionListener { notifications++ }
        fsm.toState(BTransition("NEW", To("DONE")))
        assertEquals("DONE", fsm.getState())
        assertEquals(2, notifications)
        assertFailsWith<FsmException> { fsm.toState(BTransition("NEW", To("DONE"))) }
    }

    @Test fun `JSON file and stream overloads restore scheduling`() {
        val scheduler = NamedAutoTransitionScheduler<String>("queued", AutoTransitionScheduler { _, _, _ -> })
        val table = ExTransitionTable.Builder<String, String>()
            .from("NEW").to("DONE").auto().deferWith(scheduler).end().build()
        val file = directory.resolve("flow.json")
        table.toJson(file)
        val factory = AutoTransitionSchedulerFactory<String> { scheduler }
        val restoredFile = file.fromJson({ it }, { it }, null, null, factory)
        val restoredStream = Files.newInputStream(file).use { it.fromJson({ s -> s }, { e -> e }, null, null, factory) }
        for (restored in listOf(restoredFile, restoredStream)) {
            assertSame(scheduler, restored.transitions.getValue("NEW").single().to.autoTransitionScheduler)
        }
        val plain = ExTransitionTable.Builder<String, String>().add("NEW", "GO", "DONE").build()
        val serializer = FsmJsonSerializer()
        assertEquals(plain.toDto(), serializer.deserialize(plain.toJson(), { it }, { it }).toDto())
        assertEquals(plain.toDto(), plain.toJson().byteInputStream().use {
            serializer.deserialize(it, { s -> s }, { e -> e }).toDto()
        })
        assertEquals("DONE", ToDto("DONE", emptyList(), emptyList(), emptyList(), null).toTo({ it }).state)
    }

    @Test fun `diagram export handles unlabeled edges post actions and every timeout unit`() {
        val plain = ExTransitionTable.Builder<String, String>().add("NEW", to = "DONE").build()
        assertTrue(plain.toPlantUml().contains("NEW --> DONE"))
        assertTrue(plain.toMermaid().contains("NEW --> DONE"))
        val post = NamedAction<StateContext<String>>("receipt") { }
        for (unit in TimeUnit.entries) {
            val table = ExTransitionTable.Builder<String, String>()
                .add("NEW", to = "DONE", postAction = post, timeout = Timeout(1, unit)).build()
            assertTrue(table.toMermaid().contains("receipt"))
            assertTrue(table.toPlantUml().contains("receipt"))
        }
        val plant = directory.resolve("flow.puml")
        val mermaid = directory.resolve("flow.mmd")
        plain.toPlantUml(plant)
        plain.toMermaid(mermaid)
        assertEquals(plain.toPlantUml(), Files.readString(plant))
        assertEquals(plain.toMermaid(), Files.readString(mermaid))
        val printed = capture { plain.printPlantUml(); plain.printMermaid() }
        assertTrue(printed.contains("@startuml"))
        assertTrue(printed.contains("stateDiagram-v2"))
    }

    @Test fun `transition value semantics distinguish each declarative field`() {
        val action = NamedAction<StateContext<String>>("action") { }
        val guard = NamedGuard<StateContext<String>>("guard") { true }
        assertEquals("action", action.toString())
        assertEquals("guard", guard.toString())
        val original = To("DONE")
        assertEquals(original, original)
        assertFalse(original.equals("DONE"))
        assertNotEquals(original, original.copy(state = "OTHER"))
        assertNotEquals(original, original.copy(conditions = listOf(guard)))
        assertNotEquals(original, original.copy(actions = listOf(action)))
        assertNotEquals(original, original.copy(postActions = listOf(action)))
        assertNotEquals(original, original.copy(timeout = Timeout(1)))
        assertTrue(original.toString().contains("DONE"))
        val dto = ToDto("DONE", emptyList(), emptyList(), emptyList(), null, null)
        assertFalse(dto.autoTransitionEnabled)
        assertEquals(0, FsmDto(false, emptyMap()).maxImmediateAutoTransitions)
        assertTrue(FsmTransitionFailedException("a", "b", "ORDER", "reason").message!!.contains("reason"))
        assertTrue(FsmEventSourcingTransitionFailedException("a", "go", "ORDER", "reason").message!!.contains("reason"))
    }

    private fun capture(block: () -> Unit): String {
        val original = System.out
        val bytes = ByteArrayOutputStream()
        try {
            System.setOut(PrintStream(bytes))
            block()
        } finally { System.setOut(original) }
        return bytes.toString(Charsets.UTF_8)
    }
}
