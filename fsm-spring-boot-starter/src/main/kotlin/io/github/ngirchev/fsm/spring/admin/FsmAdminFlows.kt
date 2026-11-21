package io.github.ngirchev.fsm.spring.admin

internal val FLOW_KEY = Regex("[A-Za-z0-9._-]{1,120}")

/**
 * Source of the flows the administration API exposes, asked on every request. An application that
 * creates flows at run time provides its own bean; the default one serves the [FsmAdminRegistration] beans.
 * Mutations run inside the administration transaction and under the flow-key lock of the store.
 */
interface FsmAdminFlows {
    fun all(): List<FsmAdminRegistration>

    fun find(flowKey: String): FsmAdminRegistration?

    /**
     * Registers a new flow; the API then stores its first draft. [copyOf] names the flow whose definition
     * that draft copies. Throw [IllegalArgumentException] to refuse the request (400).
     */
    fun create(flowKey: String, title: String, kind: String, description: String?, copyOf: String?): FsmAdminRegistration =
        throw UnsupportedOperationException("This application does not support creating flows")

    fun rename(flowKey: String, title: String, description: String?): FsmAdminRegistration =
        throw UnsupportedOperationException("This application does not support renaming flows")

    /** Forgets the flow; the API then removes its versions. Throw [IllegalStateException] to refuse (409). */
    fun delete(flowKey: String) {
        throw UnsupportedOperationException("This application does not support deleting flows")
    }
}

/** Fixed set of flows declared as [FsmAdminRegistration] beans. */
class RegisteredFsmAdminFlows(registrations: List<FsmAdminRegistration>) : FsmAdminFlows {
    private val registrations = registrations.associateBy { it.flowKey }

    init {
        require(this.registrations.size == registrations.size) { "Duplicate FSM admin flowKey" }
        registrations.forEach {
            require(FLOW_KEY.matches(it.flowKey)) { "Invalid FSM admin flowKey" }
            require(it.title.isNotBlank()) { "FSM admin title must not be blank" }
        }
    }

    override fun all(): List<FsmAdminRegistration> = registrations.values.toList()

    override fun find(flowKey: String): FsmAdminRegistration? = registrations[flowKey]
}
