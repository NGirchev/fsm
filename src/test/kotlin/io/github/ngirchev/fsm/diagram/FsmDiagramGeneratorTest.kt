package io.github.ngirchev.fsm.diagram

import org.junit.jupiter.api.Test
import io.github.ngirchev.fsm.Timeout
import io.github.ngirchev.fsm.To
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import io.github.ngirchev.fsm.it.document.Document
import io.github.ngirchev.fsm.it.document.DocumentState
import io.github.ngirchev.fsm.it.document.DocumentState.*

class FsmDiagramGeneratorTest {

    @Test
    fun `should generate PlantUML diagram for simple FSM`() {
        // given
        val transitionTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(from = NEW, onEvent = "TO_READY", to = READY_FOR_SIGN)
            .add(from = READY_FOR_SIGN, onEvent = "USER_SIGN", to = SIGNED, timeout = Timeout(1))
            .add(from = READY_FOR_SIGN, onEvent = "FAILED_EVENT", to = CANCELED)
            .add(from = SIGNED, onEvent = "FAILED_EVENT", to = CANCELED)
            .add(from = AUTO_SENT, onEvent = "TO_END", to = DONE)
            .build()

        val generator = PlantUmlFsmGenerator()

        // when
        val diagram = generator.generate(transitionTable)

        // then
        println("\n========== PlantUML Diagram (Simple) ==========")
        println(diagram)
        println("==============================================\n")
    }

    @Test
    fun `should generate PlantUML diagram for complex FSM with conditions`() {
        // given
        val document = Document()
        val transitionTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(from = NEW, onEvent = "TO_READY", to = READY_FOR_SIGN)
            .add(from = READY_FOR_SIGN, onEvent = "USER_SIGN", to = SIGNED, timeout = Timeout(1))
            .add(from = READY_FOR_SIGN, onEvent = "FAILED_EVENT", to = CANCELED)
            .add(from = SIGNED, onEvent = "FAILED_EVENT", to = CANCELED)
            .add(
                from = SIGNED, onEvent = "TO_END",
                To(AUTO_SENT, condition = { document.signRequired }),
                To(DONE, condition = { !document.signRequired }),
                To(CANCELED)
            )
            .add(from = AUTO_SENT, onEvent = "TO_END", to = DONE)
            .build()

        val generator = PlantUmlFsmGenerator()

        // when
        val diagram = generator.generate(transitionTable)

        // then
        println("\n========== PlantUML Diagram (Complex with Conditions) ==========")
        println(diagram)
        println("================================================================\n")
    }

    @Test
    fun `should generate Mermaid diagram for simple FSM`() {
        // given
        val transitionTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(from = NEW, onEvent = "TO_READY", to = READY_FOR_SIGN)
            .add(from = READY_FOR_SIGN, onEvent = "USER_SIGN", to = SIGNED, timeout = Timeout(1))
            .add(from = READY_FOR_SIGN, onEvent = "FAILED_EVENT", to = CANCELED)
            .add(from = SIGNED, onEvent = "FAILED_EVENT", to = CANCELED)
            .add(from = AUTO_SENT, onEvent = "TO_END", to = DONE)
            .build()

        val generator = MermaidFsmGenerator()

        // when
        val diagram = generator.generate(transitionTable)

        // then
        println("\n========== Mermaid Diagram (Simple) ==========")
        println(diagram)
        println("==============================================\n")
    }

    @Test
    fun `should generate Mermaid diagram for complex FSM with conditions`() {
        // given
        val document = Document()
        val transitionTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(from = NEW, onEvent = "TO_READY", to = READY_FOR_SIGN)
            .add(from = READY_FOR_SIGN, onEvent = "USER_SIGN", to = SIGNED, timeout = Timeout(1))
            .add(from = READY_FOR_SIGN, onEvent = "FAILED_EVENT", to = CANCELED)
            .add(from = SIGNED, onEvent = "FAILED_EVENT", to = CANCELED)
            .add(
                from = SIGNED, onEvent = "TO_END",
                To(AUTO_SENT, condition = { document.signRequired }),
                To(DONE, condition = { !document.signRequired }),
                To(CANCELED)
            )
            .add(from = AUTO_SENT, onEvent = "TO_END", to = DONE)
            .build()

        val generator = MermaidFsmGenerator()

        // when
        val diagram = generator.generate(transitionTable)

        // then
        println("\n========== Mermaid Diagram (Complex with Conditions) ==========")
        println(diagram)
        println("================================================================\n")
    }

    @Test
    fun `should generate PlantUML diagram for FSM with actions and postActions`() {
        // given
        var actionCount = 0
        var postActionCount = 0

        val transitionTable = ExTransitionTable.Builder<DocumentState, String>()
            .from(NEW).onEvent("PROCESS").to(READY_FOR_SIGN)
            .action { actionCount++ }
            .action { actionCount++ }
            .postAction { postActionCount++ }
            .condition { true }
            .timeout(Timeout(5))
            .end()
            .from(READY_FOR_SIGN).onEvent("SIGN").to(SIGNED)
            .action { actionCount++ }
            .end()
            .build()

        val generator = PlantUmlFsmGenerator()

        // when
        val diagram = generator.generate(transitionTable)

        // then
        println("\n========== PlantUML Diagram (With Actions and PostActions) ==========")
        println(diagram)
        println("=====================================================================\n")
    }

    @Test
    fun `should generate Mermaid diagram for FSM with actions and postActions`() {
        // given
        var actionCount = 0
        var postActionCount = 0

        val transitionTable = ExTransitionTable.Builder<DocumentState, String>()
            .from(NEW).onEvent("PROCESS").to(READY_FOR_SIGN)
            .action { actionCount++ }
            .action { actionCount++ }
            .postAction { postActionCount++ }
            .condition { true }
            .timeout(Timeout(5))
            .end()
            .from(READY_FOR_SIGN).onEvent("SIGN").to(SIGNED)
            .action { actionCount++ }
            .end()
            .build()

        val generator = MermaidFsmGenerator()

        // when
        val diagram = generator.generate(transitionTable)

        // then
        println("\n========== Mermaid Diagram (With Actions and PostActions) ==========")
        println(diagram)
        println("=====================================================================\n")
    }

    @Test
    fun `should generate PlantUML diagram for full Document FSM from integration test`() {
        // given
        val document = Document()
        val transitionTable = ExTransitionTable.Builder<DocumentState, String>()
            .from(NEW).to(READY_FOR_SIGN).onEvent("TO_READY").end()
            .from(READY_FOR_SIGN).toMultiple()
            .to(SIGNED).onEvent("USER_SIGN").timeout(Timeout(1)).end()
            .to(CANCELED).onEvent("FAILED_EVENT").end().endMultiple()
            .from(SIGNED).onEvent("FAILED_EVENT").to(CANCELED).end()
            .from(SIGNED).onEvent("TO_END").toMultiple()
            .to(AUTO_SENT).condition { document.signRequired }.end()
            .to(DONE).condition { !document.signRequired }.end()
            .to(CANCELED).end().endMultiple()
            .from(AUTO_SENT).onEvent("TO_END").to(DONE).end()
            .build()

        val generator = PlantUmlFsmGenerator()

        // when
        val diagram = generator.generate(transitionTable)

        // then
        println("\n========== PlantUML Diagram (Full Document FSM) ==========")
        println(diagram)
        println("==========================================================\n")
    }

    @Test
    fun `should generate Mermaid diagram for full Document FSM from integration test`() {
        // given
        val document = Document()
        val transitionTable = ExTransitionTable.Builder<DocumentState, String>()
            .from(NEW).to(READY_FOR_SIGN).onEvent("TO_READY").end()
            .from(READY_FOR_SIGN).toMultiple()
            .to(SIGNED).onEvent("USER_SIGN").timeout(Timeout(1)).end()
            .to(CANCELED).onEvent("FAILED_EVENT").end().endMultiple()
            .from(SIGNED).onEvent("FAILED_EVENT").to(CANCELED).end()
            .from(SIGNED).onEvent("TO_END").toMultiple()
            .to(AUTO_SENT).condition { document.signRequired }.end()
            .to(DONE).condition { !document.signRequired }.end()
            .to(CANCELED).end().endMultiple()
            .from(AUTO_SENT).onEvent("TO_END").to(DONE).end()
            .build()

        val generator = MermaidFsmGenerator()

        // when
        val diagram = generator.generate(transitionTable)

        // then
        println("\n========== Mermaid Diagram (Full Document FSM) ==========")
        println(diagram)
        println("==========================================================\n")
    }
}