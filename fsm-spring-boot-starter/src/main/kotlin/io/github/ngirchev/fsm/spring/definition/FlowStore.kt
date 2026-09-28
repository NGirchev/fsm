package io.github.ngirchev.fsm.spring.definition

import java.util.Optional

/** Persistence operations for versioned flows; lifecycle rules belong to [FlowService]. */
interface FlowStore {
    /** Exclusively claim the key, including a new key, until the caller's transaction ends. */
    fun lock(flowKey: String)

    /** Highest remaining version. Deleting the highest draft allows its number to be reused. */
    fun latest(flowKey: String): Optional<FlowVersion>

    fun get(flowKey: String, version: Int): Optional<FlowVersion>

    fun active(flowKey: String): Optional<FlowVersion>

    fun list(flowKey: String): List<FlowVersion>

    /** Persist a new or changed version; writes must be observable in call order. */
    fun save(version: FlowVersion): FlowVersion

    /** Physically remove a draft, including from [latest]. Called under [lock]. */
    fun deleteDraft(flowKey: String, version: Int) {
        throw UnsupportedOperationException("This flow store does not support deleting drafts")
    }
}
