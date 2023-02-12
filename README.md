# FSM
This small library contains several implementations for common use cases.
- `ru.girchev.fsm.impl.extended.ExFSM` - a simple fsm that has a status, and it changes the status by events. Has state.
- `ru.girchev.fsm.impl.extended.ExDomainFSM` - if you have some domain with a status, and you want to change this status using events. Has no own state.

You can also use the `ru.girchev.fsm.impl` package with basic implementations.

## How to use examples
### We have these initial data:
```
data class Document(
    val id: String = UUID.randomUUID().toString(),
    override var state: DocumentState = DocumentState.NEW,
    val signRequired: Boolean = false
) : FSMContext<DocumentState>

enum class DocumentState {
    NEW, READY_FOR_SIGN, SIGNED, AUTO_SENT, DONE, CANCELED
}
```
### Example of very simple use `ru.girchev.fsm.impl.extended.ExFSM`
```
    fun main() {
        val fsm = ExFSM(
            "NEW",
            ExTransitionTable.Builder<String, String>()
                .add(ExTransition(from = "NEW", to = "READY_FOR_SIGN", event = "TO_READY"))
                .add(ExTransition(from = "READY_FOR_SIGN", to = "SIGNED", event = "USER_SIGN"))
                .add(ExTransition(from = "READY_FOR_SIGN", to = "CANCELED", event = "FAILED_EVENT"))
                .add(ExTransition(from = "SIGNED", to = "AUTO_SENT"))
                .add(ExTransition(from = "AUTO_SENT", to = "DONE", event = "SUCCESS_EVENT"))
                .add(ExTransition(from = "AUTO_SENT", to = "CANCELED", event = "FAILED_EVENT"))
                .build(),
        )
        println("Initial state: ${fsm.getState()}")
        try {
            fsm.onEvent("FAILED_EVENT")
        } catch (ex: Exception) {
            println("$ex")
        }
        println("State still the same: ${fsm.getState()}")
        fsm.onEvent("TO_READY")
        fsm.onEvent("USER_SIGN")
        fsm.onEvent("SUCCESS_EVENT")
        println("Terminal state is DONE = ${fsm.getState()}")
    }
```
There are two transitions from the status `READY_FOR_SIGN`:
- `SIGNED` if event `USER_SIGN` will be thrown.
- `CANCELED` if event `FAILED_EVENT` will be thrown.
And two transitions from the status `AUTO_SENT`:
- `DONE` if event `SUCCESS_EVENT` will be thrown.
- `CANCELED` if event `FAILED_EVENT` will be thrown.


### Example for `ru.girchev.fsm.impl.extended.ExDomainFSM`.

```
fun main() {
    val document = Document(signRequired = true)
    val fsm = ExDomainFSM(
        ExTransitionTable.Builder<DocumentState, String>()
            .add(from = NEW, event = "TO_READY", to = READY_FOR_SIGN)
            .add(from = READY_FOR_SIGN, event = "USER_SIGN", to = SIGNED)
            .add(from = READY_FOR_SIGN, event = "FAILED_EVENT", to = CANCELED)
            .add(from = SIGNED, event = "FAILED_EVENT", to = CANCELED)
            .add(
                from = SIGNED, event = "TO_END",                                        // switch case example
                TransitionTable.To(AUTO_SENT, condition = { document.signRequired }),   // first
                TransitionTable.To(DONE, condition = { !document.signRequired }),       // second
                TransitionTable.To(CANCELED)                                            // else
            )
            .add(from = AUTO_SENT, event = "TO_END", to = DONE)
            .build()
    )
    try {
        fsm.handle(document, "FAILED_EVENT")
    } catch (ex: Exception) {
        println("$ex")
    }
    println("State still the same - NEW = ${document.state}")
    fsm.handle(document, "TO_READY")
    println("READY_FOR_SIGN = ${document.state}")

    fsm.handle(document, "USER_SIGN")
    println("SIGNED = ${document.state}")

    fsm.handle(document, "TO_END")
    println("AUTO_SENT = ${document.state}")

    fsm.handle(document, "TO_END")
    println("Terminal state is DONE = ${document.state}")
}
```

There are we add new extra steps. From `SIGNED` we have 3 different transitions for only one event `TO_END`:
- `AUTO_SENT` if condition `document.signRequired` will be `true`.
- `DONE` if condition `!document.signRequired` will be `true`.
- `CANCELED` if both previous conditions were `false` (definitely this case impossible, but you can change conditions for `false` in both cases).

### Example with fluent builder

We rewrite code with the same transitions
```
fun main() {
    val document = Document(signRequired = true)
    val fsm = ExDomainFSM(
        ExTransitionTable.Builder<DocumentState, String>()
            .from(NEW).to(READY_FOR_SIGN).event("TO_READY").end()

            .from(READY_FOR_SIGN).toMultiple()
            .to(SIGNED).event("USER_SIGN").end()
            .to(CANCELED).event("FAILED_EVENT").end()
            .endMultiple()

            .from(SIGNED).event("TO_END").toMultiple()
            .to(AUTO_SENT).condition { document.signRequired }.end()
            .to(DONE).condition { !document.signRequired }.end()
            .to(CANCELED).end()
            .endMultiple()

            .from(AUTO_SENT).event("TO_END").to(DONE).end()
            .build()
    )
    try {
        fsm.handle(document, "FAILED_EVENT")
    } catch (ex: Exception) {
        println("$ex")
    }
    println("State still the same - NEW = ${document.state}")
    fsm.handle(document, "TO_READY")
    println("READY_FOR_SIGN = ${document.state}")

    fsm.handle(document, "USER_SIGN")
    println("SIGNED = ${document.state}")

    fsm.handle(document, "TO_END")
    println("AUTO_SENT = ${document.state}")

    fsm.handle(document, "TO_END")
    println("Terminal state is DONE = ${document.state}")
}
```

### Example with timers - traffic light.
```
fun main() {
    val fsm = ExFSM("INITIAL", ExTransitionTable.Builder<String, String>()
        .add(ExTransition(from = "INITIAL", to = "GREEN", event = "RUN"))
        .add(ExTransition(from = "RED", to = "GREEN", timeout = Timeout(3), action = { println(it) }))
        .add(ExTransition(from = "GREEN", to = "YELLOW", timeout = Timeout(3), action = { println(it) }))
        .add(ExTransition(from = "YELLOW", to = "RED", timeout = Timeout(3), action = { println(it) }))
        .build())

    fsm.onEvent("RUN")
}
```
OR
```
fun main() {
    val fsm = ExFSM(
        "INITIAL", ExTransitionTable.Builder<String, String>()
            .from("INITIAL").to("GREEN").event("RUN").end()
            .from("RED").to("GREEN").timeout(Timeout(3)).action { println(it) }.end()
            .from("GREEN").to("YELLOW").timeout(Timeout(3)).action { println(it) }.end()
            .from("YELLOW").to("RED").timeout(Timeout(3)).action { println(it) }.end()
            .build()
    )

    fsm.onEvent("RUN")
}
```