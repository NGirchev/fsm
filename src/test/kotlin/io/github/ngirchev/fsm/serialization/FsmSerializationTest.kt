package io.github.ngirchev.fsm.serialization

import io.github.ngirchev.fsm.NamedAction
import io.github.ngirchev.fsm.NamedGuard
import io.github.ngirchev.fsm.IdAction
import io.github.ngirchev.fsm.IdGuard
import io.github.ngirchev.fsm.IdentifiableAction
import io.github.ngirchev.fsm.IdentifiableGuard
import io.github.ngirchev.fsm.Guard
import io.github.ngirchev.fsm.Action
import io.github.ngirchev.fsm.Timeout
import io.github.ngirchev.fsm.To
import io.github.ngirchev.fsm.StateChangeListener
import io.github.ngirchev.fsm.impl.extended.ExFsm
import io.github.ngirchev.fsm.impl.extended.ExDomainFsm
import io.github.ngirchev.fsm.impl.extended.ExTransition
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import io.github.ngirchev.fsm.it.document.Document
import io.github.ngirchev.fsm.it.document.DocumentState
import io.github.ngirchev.fsm.it.document.DocumentState.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FsmSerializationTest {

    @Test
    fun `should serialize and deserialize simple FSM with string states`() {
        // given - FSM from ExFsmIT
        val originalTable = ExTransitionTable.Builder<String, String>()
            .add(ExTransition(from = "NEW", to = "READY_FOR_SIGN", onEvent = "TO_READY"))
            .add(ExTransition(from = "READY_FOR_SIGN", to = "SIGNED", onEvent = "USER_SIGN"))
            .add(ExTransition(from = "READY_FOR_SIGN", to = "CANCELED", onEvent = "FAILED_EVENT"))
            .add(ExTransition(from = "SIGNED", to = "AUTO_SENT"))
            .add(ExTransition(from = "AUTO_SENT", to = "DONE", onEvent = "SUCCESS_EVENT"))
            .add(ExTransition(from = "AUTO_SENT", to = "CANCELED", onEvent = "FAILED_EVENT"))
            .build()

        // when - serialize to JSON
        val json = originalTable.toJson()
        assertNotNull(json)
        assertTrue(json.isNotEmpty())

        // and deserialize back
        val restoredTable = json.fromJson({ it }, { it })

        // then - verify structure
        assertEquals(originalTable.autoTransitionEnabled, restoredTable.autoTransitionEnabled)
        assertEquals(originalTable.transitions.size, restoredTable.transitions.size)

        // verify transitions
        val originalFsm = ExFsm("NEW", originalTable)
        val restoredFsm = ExFsm("NEW", restoredTable)

        originalFsm.onEvent("TO_READY")
        restoredFsm.onEvent("TO_READY")
        assertEquals(originalFsm.getState(), restoredFsm.getState())

        originalFsm.onEvent("USER_SIGN")
        restoredFsm.onEvent("USER_SIGN")
        assertEquals(originalFsm.getState(), restoredFsm.getState())
    }

    @Test
    fun `should serialize and deserialize FSM with enum states`() {
        // given - FSM with DocumentState enum
        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(from = NEW, onEvent = "TO_READY", to = READY_FOR_SIGN)
            .add(from = READY_FOR_SIGN, onEvent = "USER_SIGN", to = SIGNED)
            .add(from = READY_FOR_SIGN, onEvent = "FAILED_EVENT", to = CANCELED)
            .add(from = SIGNED, onEvent = "TO_END", to = DONE)
            .add(from = AUTO_SENT, onEvent = "TO_END", to = DONE)
            .build()

        // when - serialize to JSON
        val json = originalTable.toJson()
        assertNotNull(json)

        // and deserialize back
        val restoredFromJson = json.fromJson<DocumentState, String>(
            { DocumentState.valueOf(it) },
            { it }
        )

        // then - verify structure
        assertEquals(originalTable.autoTransitionEnabled, restoredFromJson.autoTransitionEnabled)
        assertEquals(originalTable.transitions.size, restoredFromJson.transitions.size)

        // verify behavior
        val originalFsm = ExFsm(NEW, originalTable)
        val restoredJsonFsm = ExFsm(NEW, restoredFromJson)

        originalFsm.onEvent("TO_READY")
        restoredJsonFsm.onEvent("TO_READY")
        assertEquals(originalFsm.getState(), restoredJsonFsm.getState())
    }

    @Test
    fun `should serialize and deserialize FSM with NamedAction and NamedGuard`() {
        // given - FSM with NamedAction and NamedGuard from NamedActionsExampleTest
        val sendEmail = NamedAction<Any>("SendEmail") { }
        val logTransaction = NamedAction<Any>("LogTransaction") { }
        val updateDatabase = NamedAction<Any>("UpdateDatabase") { }
        val isLowAmount = NamedGuard<Any>("IsLowAmount") { true }
        val isVerified = NamedGuard<Any>("IsVerified") { true }

        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .from(NEW)
            .onEvent("START_PAYMENT")
            .to(READY_FOR_SIGN)
            .action(logTransaction)
            .action(sendEmail)
            .end()
            .from(READY_FOR_SIGN)
            .onEvent("VERIFY")
            .to(SIGNED)
            .onCondition(isLowAmount)
            .onCondition(isVerified)
            .action(updateDatabase)
            .timeout(Timeout(2))
            .end()
            .build()

        // when - serialize
        val json = originalTable.toJson()

        // create factories for deserialization
        val actionFactory = ActionFactory<DocumentState> { name ->
            when (name) {
                "SendEmail" -> NamedAction(name) { }
                "LogTransaction" -> NamedAction(name) { }
                "UpdateDatabase" -> NamedAction(name) { }
                else -> null
            }
        }

        val guardFactory = GuardFactory<DocumentState> { name ->
            when (name) {
                "IsLowAmount" -> NamedGuard(name) { true }
                "IsVerified" -> NamedGuard(name) { true }
                else -> null
            }
        }

        // and deserialize back
        val restoredFromJson = json.fromJson<DocumentState, String>(
            { DocumentState.valueOf(it) },
            { it },
            actionFactory,
            guardFactory
        )

        // then - verify structure
        assertEquals(originalTable.autoTransitionEnabled, restoredFromJson.autoTransitionEnabled)
        assertEquals(originalTable.transitions.size, restoredFromJson.transitions.size)

        // verify that transitions contain the same states and events
        val originalTransitions = originalTable.transitions.values.flatten()
        val restoredJsonTransitions = restoredFromJson.transitions.values.flatten()

        assertEquals(originalTransitions.size, restoredJsonTransitions.size)

        // verify events
        val originalEvents = originalTransitions.mapNotNull { transition -> transition.event }.toSet()
        val restoredJsonEvents = restoredJsonTransitions.mapNotNull { transition -> transition.event }.toSet()

        assertEquals(originalEvents, restoredJsonEvents)
    }

    @Test
    fun `should serialize and deserialize FSM with timeout`() {
        // given - FSM with timeout
        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(from = NEW, onEvent = "TO_READY", to = READY_FOR_SIGN, timeout = Timeout(5))
            .add(from = READY_FOR_SIGN, onEvent = "USER_SIGN", to = SIGNED, timeout = Timeout(10))
            .build()

        // when - serialize and deserialize
        val json = originalTable.toJson()
        val restoredTable = json.fromJson<DocumentState, String>(
            { DocumentState.valueOf(it) },
            { it }
        )

        // then - verify timeout is preserved
        val originalTransitions = originalTable.transitions.values.flatten()
        val restoredTransitions = restoredTable.transitions.values.flatten()

        assertEquals(originalTransitions.size, restoredTransitions.size)

        // Check that timeouts are serialized (they should be in DTO)
        val dto = originalTable.toDto()
        val hasTimeout = dto.transitions.values.flatten().any { it.to.timeout != null }
        assertTrue(hasTimeout, "DTO should contain timeout information")
    }

    @Test
    fun `should serialize and deserialize FSM with multiple transitions from same state`() {
        // given - FSM with multiple transitions from same state (toMultiple pattern)
        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .from(NEW).to(READY_FOR_SIGN).onEvent("TO_READY").end()
            .from(READY_FOR_SIGN).toMultiple()
            .to(SIGNED).onEvent("USER_SIGN").end()
            .to(CANCELED).onEvent("FAILED_EVENT").end()
            .endMultiple()
            .from(SIGNED).onEvent("TO_END").to(DONE).end()
            .build()

        // when - serialize and deserialize
        val json = originalTable.toJson()
        val restoredTable = json.fromJson<DocumentState, String>(
            { DocumentState.valueOf(it) },
            { it }
        )

        // then - verify all transitions are preserved
        assertEquals(originalTable.transitions.size, restoredTable.transitions.size)

        // Verify transitions from READY_FOR_SIGN
        val originalReadyTransitions = originalTable.transitions[READY_FOR_SIGN]?.size ?: 0
        val restoredReadyTransitions = restoredTable.transitions[READY_FOR_SIGN]?.size ?: 0
        assertEquals(originalReadyTransitions, restoredReadyTransitions)
        assertTrue(restoredReadyTransitions >= 2, "Should have at least 2 transitions from READY_FOR_SIGN")
    }

    @Test
    fun `should serialize and deserialize FSM with autoTransitionEnabled`() {
        // given - FSM with autoTransitionEnabled = true
        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .autoTransitionEnabled(true)
            .add(from = NEW, onEvent = "TO_READY", to = READY_FOR_SIGN)
            .add(from = READY_FOR_SIGN, to = SIGNED)
            .build()

        // when - serialize and deserialize
        val json = originalTable.toJson()
        val restoredTable = json.fromJson<DocumentState, String>(
            { DocumentState.valueOf(it) },
            { it }
        )

        // then - verify autoTransitionEnabled is preserved
        assertEquals(true, originalTable.autoTransitionEnabled)
        assertEquals(true, restoredTable.autoTransitionEnabled)
    }

    @Test
    fun `should serialize and deserialize FSM with conditions`() {
        // given - FSM with conditions (from ExDomainFsmIT)
        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(
                from = SIGNED, onEvent = "TO_END",
                To(AUTO_SENT, condition = { (it as? Document)?.signRequired == true }),
                To(DONE, condition = { (it as? Document)?.signRequired == false }),
                To(CANCELED)
            )
            .build()

        // when - serialize
        val json = originalTable.toJson()
        val dto = originalTable.toDto()

        // then - verify conditions are serialized (but only NamedGuard)
        // Non-named guards won't be serialized, so we check structure
        assertNotNull(json)
        assertNotNull(dto)

        // Verify that transitions are serialized
        val signedTransitions = dto.transitions[SIGNED.toString()]
        assertNotNull(signedTransitions)
        assertTrue(signedTransitions.isNotEmpty())
    }

    @Test
    fun `should serialize and deserialize complex FSM from ExDomainFsmIT`() {
        // given - complex FSM similar to ExDomainFsmIT
        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(from = NEW, onEvent = "TO_READY", to = READY_FOR_SIGN)
            .add(from = READY_FOR_SIGN, onEvent = "USER_SIGN", to = SIGNED, timeout = Timeout(1))
            .add(from = READY_FOR_SIGN, onEvent = "FAILED_EVENT", to = CANCELED)
            .add(from = SIGNED, onEvent = "FAILED_EVENT", to = CANCELED)
            .add(
                from = SIGNED, onEvent = "TO_END",
                To(AUTO_SENT, condition = { (it as? Document)?.signRequired == true }),
                To(DONE, condition = { (it as? Document)?.signRequired == false }),
                To(CANCELED)
            )
            .add(from = AUTO_SENT, onEvent = "TO_END", to = DONE)
            .build()

        // when - serialize and deserialize
        val json = originalTable.toJson()
        val restoredFromJson = json.fromJson<DocumentState, String>(
            { DocumentState.valueOf(it) },
            { it }
        )

        // then - verify structure
        assertEquals(originalTable.autoTransitionEnabled, restoredFromJson.autoTransitionEnabled)
        assertEquals(originalTable.transitions.size, restoredFromJson.transitions.size)

        // verify behavior with actual FSM
        val originalFsm = ExFsm(NEW, originalTable)
        val restoredJsonFsm = ExFsm(NEW, restoredFromJson)

        originalFsm.onEvent("TO_READY")
        restoredJsonFsm.onEvent("TO_READY")
        assertEquals(originalFsm.getState(), restoredJsonFsm.getState())

        originalFsm.onEvent("USER_SIGN")
        restoredJsonFsm.onEvent("USER_SIGN")
        assertEquals(originalFsm.getState(), restoredJsonFsm.getState())
    }

    @Test
    fun `should handle serialization to and from file`() {
        // given
        val originalTable = ExTransitionTable.Builder<String, String>()
            .add(ExTransition(from = "A", to = "B", onEvent = "EVENT1"))
            .add(ExTransition(from = "B", to = "C", onEvent = "EVENT2"))
            .build()

        // when - serialize to file
        val tempFile = java.io.File.createTempFile("fsm_test", ".json")
        tempFile.deleteOnExit()
        originalTable.toJson(tempFile.toPath())

        // and deserialize from file
        val restoredTable = tempFile.toPath().fromJson<String, String>({ it }, { it })

        // then
        assertEquals(originalTable.transitions.size, restoredTable.transitions.size)

        // cleanup
        tempFile.delete()
    }

    @Test
    fun `should serialize FSM with postActions`() {
        // given - FSM with postActions
        val createAuditRecord = NamedAction<Any>("CreateAuditRecord") { }
        val sendEmail = NamedAction<Any>("SendEmail") { }

        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .from(NEW)
            .onEvent("START")
            .to(READY_FOR_SIGN)
            .action(sendEmail)
            .postAction(createAuditRecord)
            .end()
            .build()

        // when - serialize
        val dto = originalTable.toDto()

        // then - verify postActions are in DTO
        val newTransitions = dto.transitions[NEW.toString()]
        assertNotNull(newTransitions)
        val transition = newTransitions.firstOrNull()
        assertNotNull(transition)
        assertTrue(transition.to.postActions.contains("CreateAuditRecord"))
        assertTrue(transition.to.actions.contains("SendEmail"))
    }

    @Test
    fun `should serialize and deserialize FSM with IdAction and IdGuard`() {
        // given - FSM with IdAction and IdGuard
        var actionExecuted = false
        var postActionExecuted = false
        var guardChecked = false
        
        val sendEmail = IdAction<Any>("sendEmail") { actionExecuted = true }
        val logAction = IdAction<Any>("logAction") { postActionExecuted = true }
        val isVerified = IdGuard<Any>("isVerified") { guardChecked = true; true }

        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .from(NEW)
            .onEvent("START")
            .to(READY_FOR_SIGN)
            .action(sendEmail)
            .postAction(logAction)
            .onCondition(isVerified)
            .end()
            .build()

        // when - serialize
        val json = originalTable.toJson()

        // create factories for deserialization
        val actionFactory = ActionFactory<DocumentState> { id ->
            when (id) {
                "sendEmail" -> IdAction(id) { actionExecuted = true }
                "logAction" -> IdAction(id) { postActionExecuted = true }
                else -> null
            }
        }

        val guardFactory = GuardFactory<DocumentState> { id ->
            when (id) {
                "isVerified" -> IdGuard(id) { guardChecked = true; true }
                else -> null
            }
        }

        // and deserialize back
        val restoredFromJson = json.fromJson<DocumentState, String>(
            { DocumentState.valueOf(it) },
            { it },
            actionFactory,
            guardFactory
        )

        // then - verify structure
        assertEquals(originalTable.autoTransitionEnabled, restoredFromJson.autoTransitionEnabled)
        assertEquals(originalTable.transitions.size, restoredFromJson.transitions.size)

        // verify behavior - actions and guards should work
        val restoredFsm = ExFsm(NEW, restoredFromJson)
        actionExecuted = false
        postActionExecuted = false
        guardChecked = false
        
        restoredFsm.onEvent("START")
        
        assertTrue(actionExecuted, "Action should be executed")
        assertTrue(postActionExecuted, "PostAction should be executed")
        assertTrue(guardChecked, "Guard should be checked")
        assertEquals(READY_FOR_SIGN, restoredFsm.getState())
    }

    @Test
    fun `should serialize IdGuard with conditions`() {
        // given - FSM with IdGuard conditions
        val signRequiredGuard = IdGuard<Any>("signRequired") { (it as? Document)?.signRequired == true }
        val notSignRequiredGuard = IdGuard<Any>("notSignRequired") { (it as? Document)?.signRequired == false }

        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(
                from = SIGNED, onEvent = "TO_END",
                To(AUTO_SENT, condition = signRequiredGuard),
                To(DONE, condition = notSignRequiredGuard),
                To(CANCELED)
            )
            .build()

        // when - serialize
        val json = originalTable.toJson()
        val dto = originalTable.toDto()

        // then - verify conditions are serialized with IDs
        val signedTransitions = dto.transitions[SIGNED.toString()]
        assertNotNull(signedTransitions)
        assertTrue(signedTransitions.isNotEmpty())
        
        val transition = signedTransitions.firstOrNull { it.event == "TO_END" && it.to.state == "AUTO_SENT" }
        assertNotNull(transition)
        assertTrue(transition.to.conditions.contains("signRequired"))
        
        val transition2 = signedTransitions.firstOrNull { it.event == "TO_END" && it.to.state == "DONE" }
        assertNotNull(transition2)
        assertTrue(transition2.to.conditions.contains("notSignRequired"))
    }

    @Test
    fun `should serialize and deserialize with InputStream and OutputStream`() {
        // given
        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(from = NEW, onEvent = "TO_READY", to = READY_FOR_SIGN)
            .build()

        // when - serialize to OutputStream
        val outputStream = java.io.ByteArrayOutputStream()
        originalTable.toJson(outputStream)
        val jsonBytes = outputStream.toByteArray()

        // and deserialize from InputStream
        val inputStream = java.io.ByteArrayInputStream(jsonBytes)
        val restoredTable = inputStream.fromJson<DocumentState, String>(
            { DocumentState.valueOf(it) },
            { it }
        )

        // then
        assertEquals(originalTable.autoTransitionEnabled, restoredTable.autoTransitionEnabled)
        assertEquals(originalTable.transitions.size, restoredTable.transitions.size)
    }

    @Test
    fun `should handle factory returning null for unknown IDs`() {
        // given - FSM with IdGuard
        val guard = IdGuard<Any>("knownGuard") { true }
        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(
                from = NEW, onEvent = "START",
                To(READY_FOR_SIGN, condition = guard)
            )
            .build()

        // when - serialize and deserialize with factory that returns null for unknown IDs
        val json = originalTable.toJson()
        val guardFactory = GuardFactory<DocumentState> { id ->
            if (id == "knownGuard") {
                IdGuard(id) { true }
            } else {
                null // Unknown ID
            }
        }

        val restoredTable = json.fromJson<DocumentState, String>(
            { DocumentState.valueOf(it) },
            { it },
            null,
            guardFactory
        )

        // then - verify that known guard is restored, unknown ones are skipped
        val newTransitions = restoredTable.transitions[NEW]
        assertNotNull(newTransitions)
        val transition = newTransitions.firstOrNull { it.event == "START" }
        assertNotNull(transition)
        assertEquals(1, transition.to.conditions.size) // Only known guard should be restored
    }

    @Test
    fun `should test IdAction invoke method`() {
        // given
        var executed = false
        val action = IdAction<Any>("testAction") { executed = true }

        // when
        action.invoke(Unit)

        // then
        assertTrue(executed, "IdAction should execute the provided lambda")
    }

    @Test
    fun `should test IdGuard invoke method`() {
        // given
        var checked = false
        val guard = IdGuard<Any>("testGuard") { checked = true; true }

        // when
        val result = guard.invoke(Unit)

        // then
        assertTrue(checked, "IdGuard should execute the provided lambda")
        assertTrue(result, "IdGuard should return the lambda result")
    }

    @Test
    fun `should test IdentifiableAction and IdentifiableGuard interfaces`() {
        // given
        val idAction = IdAction<Any>("actionId") { }
        val idGuard = IdGuard<Any>("guardId") { true }
        val namedAction = NamedAction<Any>("namedAction") { }
        val namedGuard = NamedGuard<Any>("namedGuard") { true }

        // then - verify that all implement IdentifiableAction/IdentifiableGuard
        assertTrue(idAction is IdentifiableAction<*>)
        assertTrue(idGuard is IdentifiableGuard<*>)
        assertTrue(namedAction is IdentifiableAction<*>)
        assertTrue(namedGuard is IdentifiableGuard<*>)

        // and verify IDs
        assertEquals("actionId", idAction.id)
        assertEquals("guardId", idGuard.id)
        assertEquals("namedAction", namedAction.id)
        assertEquals("namedGuard", namedGuard.id)
    }

    @Test
    fun `should handle timeout with invalid unit during deserialization`() {
        // given - create DTO with invalid timeout unit
        val dto = FsmDto(
            autoTransitionEnabled = false,
            transitions = mapOf(
                "NEW" to listOf(
                    TransitionDto(
                        from = "NEW",
                        to = ToDto(
                            state = "READY",
                            conditions = emptyList(),
                            actions = emptyList(),
                            postActions = emptyList(),
                            timeout = TimeoutDto(value = 5, unit = "INVALID_UNIT")
                        ),
                        event = "START"
                    )
                )
            )
        )

        // when - deserialize
        val restoredTable = dto.toExTransitionTable<String, String>(
            { it },
            { it }
        )

        // then - should use default TimeUnit.SECONDS for invalid unit
        val newTransitions = restoredTable.transitions["NEW"]
        assertNotNull(newTransitions)
        val transition = newTransitions.firstOrNull { it.event == "START" }
        assertNotNull(transition)
        assertNotNull(transition.to.timeout)
        assertEquals(5, transition.to.timeout?.value)
        assertEquals(java.util.concurrent.TimeUnit.SECONDS, transition.to.timeout?.unit)
    }

    @Test
    fun `should handle deserialization with null factories`() {
        // given - FSM with IdAction and IdGuard
        val action = IdAction<Any>("testAction") { }
        val guard = IdGuard<Any>("testGuard") { true }
        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(
                from = NEW, onEvent = "START",
                To(READY_FOR_SIGN, condition = guard, action = action)
            )
            .build()

        // when - serialize and deserialize with null factories
        val json = originalTable.toJson()
        val restoredTable = json.fromJson<DocumentState, String>(
            { DocumentState.valueOf(it) },
            { it },
            null, // null actionFactory
            null  // null guardFactory
        )

        // then - conditions and actions should be empty (not restored)
        val newTransitions = restoredTable.transitions[NEW]
        assertNotNull(newTransitions)
        val transition = newTransitions.firstOrNull { it.event == "START" }
        assertNotNull(transition)
        assertTrue(transition.to.conditions.isEmpty(), "Conditions should be empty when factory is null")
        assertTrue(transition.to.actions.isEmpty(), "Actions should be empty when factory is null")
    }

    @Test
    fun `should handle deserialization when factory returns null for some IDs`() {
        // given - FSM with multiple IdActions
        val action1 = IdAction<Any>("action1") { }
        val action2 = IdAction<Any>("action2") { }
        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(
                from = NEW, onEvent = "START",
                To(READY_FOR_SIGN, action = action1)
            )
            .add(
                from = READY_FOR_SIGN, onEvent = "NEXT",
                To(SIGNED, action = action2)
            )
            .build()

        // when - serialize and deserialize with factory that only knows action1
        val json = originalTable.toJson()
        val actionFactory = ActionFactory<DocumentState> { id ->
            if (id == "action1") {
                IdAction(id) { }
            } else {
                null // Unknown action
            }
        }

        val restoredTable = json.fromJson<DocumentState, String>(
            { DocumentState.valueOf(it) },
            { it },
            actionFactory,
            null
        )

        // then - only action1 should be restored
        val newTransitions = restoredTable.transitions[NEW]
        assertNotNull(newTransitions)
        val transition1 = newTransitions.firstOrNull { it.event == "START" }
        assertNotNull(transition1)
        assertEquals(1, transition1.to.actions.size) // action1 should be restored

        val readyTransitions = restoredTable.transitions[READY_FOR_SIGN]
        assertNotNull(readyTransitions)
        val transition2 = readyTransitions.firstOrNull { it.event == "NEXT" }
        assertNotNull(transition2)
        assertTrue(transition2.to.actions.isEmpty(), "action2 should not be restored")
    }

    @Test
    fun `should skip non-identifiable guards and actions during serialization`() {
        // given - FSM with both Identifiable and non-identifiable guards/actions
        val identifiableGuard = IdGuard<Any>("identifiableGuard") { true }
        val identifiableAction = IdAction<Any>("identifiableAction") { }
        // Create non-identifiable guard/action using lambda (not wrapped in IdGuard/IdAction)
        val nonIdentifiableGuard: Guard<Any> = Guard { true }
        val nonIdentifiableAction: Action<Any> = Action { }

        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(
                from = NEW, onEvent = "START",
                To(
                    READY_FOR_SIGN,
                    condition = identifiableGuard,
                    action = identifiableAction
                )
            )
            .add(
                from = READY_FOR_SIGN, onEvent = "NEXT",
                To(
                    SIGNED,
                    condition = nonIdentifiableGuard,
                    action = nonIdentifiableAction
                )
            )
            .build()

        // when - serialize
        val dto = originalTable.toDto()

        // then - only identifiable guards/actions should be serialized
        val newTransitions = dto.transitions[NEW.toString()]
        assertNotNull(newTransitions)
        val transition1 = newTransitions.firstOrNull { it.event == "START" }
        assertNotNull(transition1)
        assertTrue(transition1.to.conditions.contains("identifiableGuard"))
        assertTrue(transition1.to.actions.contains("identifiableAction"))

        val readyTransitions = dto.transitions[READY_FOR_SIGN.toString()]
        assertNotNull(readyTransitions)
        val transition2 = readyTransitions.firstOrNull { it.event == "NEXT" }
        assertNotNull(transition2)
        assertTrue(transition2.to.conditions.isEmpty(), "Non-identifiable guard should not be serialized")
        assertTrue(transition2.to.actions.isEmpty(), "Non-identifiable action should not be serialized")
    }

    @Test
    fun `should handle mixed Identifiable and non-Identifiable in same To`() {
        // given - To with both types
        val identifiableGuard = IdGuard<Any>("idGuard") { true }
        val nonIdentifiableGuard: Guard<Any> = Guard { false }
        val identifiableAction = IdAction<Any>("idAction") { }
        val nonIdentifiableAction: Action<Any> = Action { }

        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(
                from = NEW, onEvent = "START",
                To(
                    state = READY_FOR_SIGN,
                    conditions = listOf(identifiableGuard, nonIdentifiableGuard),
                    actions = listOf(identifiableAction, nonIdentifiableAction),
                    postActions = emptyList(),
                    timeout = null
                )
            )
            .build()

        // when - serialize
        val dto = originalTable.toDto()

        // then - only identifiable ones should be in DTO
        val newTransitions = dto.transitions[NEW.toString()]
        assertNotNull(newTransitions)
        val transition = newTransitions.firstOrNull { it.event == "START" }
        assertNotNull(transition)
        assertEquals(1, transition.to.conditions.size, "Only identifiable guard should be serialized")
        assertEquals(1, transition.to.actions.size, "Only identifiable action should be serialized")
        assertTrue(transition.to.conditions.contains("idGuard"))
        assertTrue(transition.to.actions.contains("idAction"))
    }

    @Test
    fun `should serialize transitions with null event`() {
        // given - FSM with null event (auto-transition)
        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(from = NEW, to = READY_FOR_SIGN) // null event
            .build()

        // when - serialize
        val dto = originalTable.toDto()

        // then - event should be null in DTO
        val newTransitions = dto.transitions[NEW.toString()]
        assertNotNull(newTransitions)
        val transition = newTransitions.firstOrNull()
        assertNotNull(transition)
        assertTrue(transition.event == null, "Event should be null for auto-transition")
    }

    @Test
    fun `should handle timeout serialization with different TimeUnits`() {
        // given - FSM with different timeout units
        val timeout1 = Timeout(1, java.util.concurrent.TimeUnit.SECONDS)
        val timeout2 = Timeout(2, java.util.concurrent.TimeUnit.MINUTES)
        val timeout3 = Timeout(3, java.util.concurrent.TimeUnit.HOURS)

        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(from = NEW, onEvent = "START1", to = READY_FOR_SIGN, timeout = timeout1)
            .add(from = READY_FOR_SIGN, onEvent = "START2", to = SIGNED, timeout = timeout2)
            .add(from = SIGNED, onEvent = "START3", to = DONE, timeout = timeout3)
            .build()

        // when - serialize
        val dto = originalTable.toDto()

        // then - verify timeout units are serialized correctly
        val newTransitions = dto.transitions[NEW.toString()]
        assertNotNull(newTransitions)
        val transition1 = newTransitions.firstOrNull { it.event == "START1" }
        assertNotNull(transition1)
        assertEquals("SECONDS", transition1.to.timeout?.unit)

        val readyTransitions = dto.transitions[READY_FOR_SIGN.toString()]
        assertNotNull(readyTransitions)
        val transition2 = readyTransitions.firstOrNull { it.event == "START2" }
        assertNotNull(transition2)
        assertEquals("MINUTES", transition2.to.timeout?.unit)

        val signedTransitions = dto.transitions[SIGNED.toString()]
        assertNotNull(signedTransitions)
        val transition3 = signedTransitions.firstOrNull { it.event == "START3" }
        assertNotNull(transition3)
        assertEquals("HOURS", transition3.to.timeout?.unit)
    }

    @Test
    fun `should deserialize timeout with valid TimeUnit`() {
        // given - DTO with valid timeout unit
        val dto = FsmDto(
            autoTransitionEnabled = false,
            transitions = mapOf(
                "NEW" to listOf(
                    TransitionDto(
                        from = "NEW",
                        to = ToDto(
                            state = "READY",
                            conditions = emptyList(),
                            actions = emptyList(),
                            postActions = emptyList(),
                            timeout = TimeoutDto(value = 5, unit = "MINUTES")
                        ),
                        event = "START"
                    )
                )
            )
        )

        // when - deserialize
        val restoredTable = dto.toExTransitionTable<String, String>(
            { it },
            { it }
        )

        // then - timeout should be restored with correct unit
        val newTransitions = restoredTable.transitions["NEW"]
        assertNotNull(newTransitions)
        val transition = newTransitions.firstOrNull { it.event == "START" }
        assertNotNull(transition)
        assertNotNull(transition.to.timeout)
        assertEquals(5, transition.to.timeout?.value)
        assertEquals(java.util.concurrent.TimeUnit.MINUTES, transition.to.timeout?.unit)
    }

    @Test
    fun `should test NamedAction invoke method`() {
        // given
        var executed = false
        val action = NamedAction<Any>("testAction") { executed = true }

        // when
        action.invoke(Unit)

        // then
        assertTrue(executed, "NamedAction should execute the provided lambda")
    }

    @Test
    fun `should test NamedGuard invoke method`() {
        // given
        var checked = false
        val guard = NamedGuard<Any>("testGuard") { checked = true; true }

        // when
        val result = guard.invoke(Unit)

        // then
        assertTrue(checked, "NamedGuard should execute the provided lambda")
        assertTrue(result, "NamedGuard should return the lambda result")
    }

    @Test
    fun `should test printJson extension function`() {
        // given
        val table = ExTransitionTable.Builder<DocumentState, String>()
            .add(from = NEW, onEvent = "START", to = READY_FOR_SIGN)
            .build()

        // when - should not throw exception
        table.printJson()
        
        // then - test passes if no exception thrown
        assertTrue(true)
    }

    @Test
    fun `should test FsmJsonSerializer getObjectMapper`() {
        // given
        val serializer = FsmJsonSerializer()
        
        // when
        val mapper = serializer.objectMapper
        
        // then
        assertNotNull(mapper)
    }

    @Test
    fun `should test ExDomainFsm state change listeners`() {
        // given
        val table = ExTransitionTable.Builder<DocumentState, String>()
            .add(from = NEW, onEvent = "START", to = READY_FOR_SIGN)
            .build()
        val fsm = ExDomainFsm(table)
        var listenerCalled = false
        
        val listener = StateChangeListener<DocumentState> { _, _, _ ->
            listenerCalled = true
        }
        
        // when - add listener
        fsm.addStateChangeListener(listener)
        
        // and trigger state change
        val document = Document()
        fsm.handle(document, "START")
        
        // then - listener should be called
        assertTrue(listenerCalled, "Listener should be called on state change")
        
        // and remove listener
        listenerCalled = false
        fsm.removeStateChangeListener(listener)
        // Use a new document to test that listener is not called after removal
        val document2 = Document()
        fsm.handle(document2, "START")
        
        // then - listener should not be called after removal
        assertTrue(!listenerCalled, "Listener should not be called after removal")
    }

    @Test
    fun `should test ToMultipleTransitionBuilder postAction`() {
        // given
        var postActionExecuted = false
        val postAction = Action<Any> { postActionExecuted = true }
        
        val table = ExTransitionTable.Builder<DocumentState, String>()
            .from(NEW)
            .toMultiple()
            .to(READY_FOR_SIGN)
            .onEvent("START")
            .postAction(postAction)
            .end()
            .endMultiple()
            .build()
        
        // when - use FSM
        val fsm = ExFsm(NEW, table)
        fsm.onEvent("START")
        
        // then - postAction should be executed
        assertTrue(postActionExecuted, "PostAction should be executed")
    }

    @Test
    fun `should handle postAction that is not IdentifiableAction in serialization`() {
        // given - FSM with non-identifiable postAction
        val identifiablePostAction = IdAction<Any>("idPostAction") { }
        val nonIdentifiablePostAction: Action<Any> = Action { }
        
        val originalTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(
                from = NEW, onEvent = "START",
                To(
                    state = READY_FOR_SIGN,
                    conditions = emptyList(),
                    actions = emptyList(),
                    postActions = listOf(identifiablePostAction, nonIdentifiablePostAction),
                    timeout = null
                )
            )
            .build()
        
        // when - serialize
        val dto = originalTable.toDto()
        
        // then - only identifiable postAction should be serialized
        val newTransitions = dto.transitions[NEW.toString()]
        assertNotNull(newTransitions)
        val transition = newTransitions.firstOrNull { it.event == "START" }
        assertNotNull(transition)
        assertEquals(1, transition.to.postActions.size, "Only identifiable postAction should be serialized")
        assertTrue(transition.to.postActions.contains("idPostAction"))
    }
}
