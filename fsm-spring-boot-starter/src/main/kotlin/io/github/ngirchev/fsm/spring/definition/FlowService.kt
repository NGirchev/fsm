package io.github.ngirchev.fsm.spring.definition

import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
open class FlowService(
    private val store: FlowStore,
    private val loader: FlowLoader,
) {
    @Transactional
    open fun createDraft(flowKey: String?, definition: FlowDefinition): FlowVersion {
        val key = requireFlowKey(flowKey)
        store.lock(key)
        val latest = store.latest(key)
        val nextVersion = if (latest.isPresent) latest.get().version + 1 else 1
        return store.save(FlowVersion(key, nextVersion, FlowVersionStatus.DRAFT, definition))
    }

    @Transactional
    open fun updateDraft(flowKey: String?, version: Int, definition: FlowDefinition): FlowVersion {
        val key = requireFlowKey(flowKey)
        store.lock(key)
        val current = store.get(key, version)
            .orElseThrow { IllegalStateException("Only an existing draft version can be changed") }
        check(current.status == FlowVersionStatus.DRAFT) { "Only an existing draft version can be changed" }
        return store.save(FlowVersion(key, version, FlowVersionStatus.DRAFT, definition))
    }

    @Transactional
    open fun deleteDraft(flowKey: String?, version: Int) {
        val key = requireFlowKey(flowKey)
        store.lock(key)
        val target = get(key, version)
        check(target.status == FlowVersionStatus.DRAFT) { "Only a draft version can be deleted" }
        store.deleteDraft(key, version)
    }

    open fun list(flowKey: String?): List<FlowVersion> = store.list(requireFlowKey(flowKey))

    open fun get(flowKey: String?, version: Int): FlowVersion =
        store.get(requireFlowKey(flowKey), version)
            .orElseThrow { NoSuchElementException("Flow $flowKey version $version was not found") }

    open fun active(flowKey: String?): FlowVersion =
        store.active(requireFlowKey(flowKey))
            .orElseThrow { NoSuchElementException("Flow $flowKey has no active version") }

    @Transactional
    open fun publish(flowKey: String?, version: Int): FlowVersion {
        val key = requireFlowKey(flowKey)
        store.lock(key)
        val target = store.get(key, version)
            .orElseThrow { NoSuchElementException("Flow $key version $version was not found") }
        check(target.status == FlowVersionStatus.DRAFT) { "Only a draft version can be published" }
        loader.load(target.definition)
        store.active(key).ifPresent { active ->
            store.save(active.copy(status = FlowVersionStatus.ARCHIVED))
        }
        return store.save(target.copy(status = FlowVersionStatus.ACTIVE))
    }

    @Transactional
    open fun activate(flowKey: String?, version: Int): FlowVersion {
        val key = requireFlowKey(flowKey)
        store.lock(key)
        val target = store.get(key, version)
            .orElseThrow { NoSuchElementException("Flow $key version $version was not found") }
        check(target.status == FlowVersionStatus.ARCHIVED) { "Only an archived version can be activated" }
        loader.load(target.definition)
        store.active(key).ifPresent { active ->
            store.save(active.copy(status = FlowVersionStatus.ARCHIVED))
        }
        return store.save(target.copy(status = FlowVersionStatus.ACTIVE))
    }

    private fun requireFlowKey(flowKey: String?): String {
        require(flowKey != null && FLOW_KEY.matches(flowKey)) {
            "flowKey must contain 1-120 letters, digits, dots, underscores or hyphens"
        }
        return flowKey
    }

    private companion object {
        val FLOW_KEY = Regex("[A-Za-z0-9._-]{1,120}")
    }
}
