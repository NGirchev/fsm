package io.github.ngirchev.fsm.spring.definition

import io.github.ngirchev.fsm.serialization.FsmDto
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import java.util.Optional

class FlowServiceTest {
    private val store = TestStore()
    private val loader = mock(FlowLoader::class.java)
    private val service = FlowService(store, loader)
    private val definition = FlowDefinition("NEW", FsmDto(false, emptyMap()))
    private val changed = FlowDefinition("READY", FsmDto(false, emptyMap()))

    @Test
    fun `starter owns draft and publication lifecycle`() {
        val first = service.createDraft("order", definition)
        val second = service.createDraft("order", definition)
        assertThat(first.version).isEqualTo(1)
        assertThat(second.version).isEqualTo(2)
        assertThat(service.updateDraft("order", 1, changed).definition).isEqualTo(changed)
        assertThat(service.list("order")).hasSize(2)
        assertThat(service.get("order", 1).status).isEqualTo(FlowVersionStatus.DRAFT)

        service.publish("order", 1)
        assertThat(service.active("order").version).isEqualTo(1)
        service.publish("order", 2)
        assertThat(service.get("order", 1).status).isEqualTo(FlowVersionStatus.ARCHIVED)
        assertThat(service.active("order").version).isEqualTo(2)
        assertThat(store.locks).isEqualTo(5)
        verify(loader).load(changed)
        verify(loader).load(definition)
        assertThat(FlowVersionStatus.entries).containsExactly(
            FlowVersionStatus.DRAFT,
            FlowVersionStatus.ACTIVE,
            FlowVersionStatus.ARCHIVED,
        )
    }

    @Test
    fun `errors do not change published versions`() {
        assertThatThrownBy { service.active("missing") }.isInstanceOf(NoSuchElementException::class.java)
        assertThatThrownBy { service.get("missing", 1) }.isInstanceOf(NoSuchElementException::class.java)
        assertThatThrownBy { service.updateDraft("order", 1, definition) }
            .isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { service.publish("order", 1) }.isInstanceOf(NoSuchElementException::class.java)

        service.createDraft("order", definition)
        doThrow(IllegalArgumentException("invalid")).`when`(loader).load(definition)
        assertThatThrownBy { service.publish("order", 1) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(service.get("order", 1).status).isEqualTo(FlowVersionStatus.DRAFT)
        reset(loader)
        service.publish("order", 1)
        assertThatThrownBy { service.updateDraft("order", 1, changed) }
            .isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { service.publish("order", 1) }.isInstanceOf(IllegalStateException::class.java)
        assertThat(service.active("order").definition).isEqualTo(definition)
    }

    @Test
    fun `archived version can become active again without changing existing versions`() {
        service.createDraft("order", definition)
        service.publish("order", 1)
        service.createDraft("order", changed)
        service.publish("order", 2)

        assertThat(service.activate("order", 1).status).isEqualTo(FlowVersionStatus.ACTIVE)
        assertThat(service.get("order", 2).status).isEqualTo(FlowVersionStatus.ARCHIVED)
        assertThat(service.active("order").version).isEqualTo(1)
        assertThatThrownBy { service.activate("order", 1) }.isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { service.activate("order", 999) }.isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun `deletes only drafts and reuses the highest deleted version`() {
        service.createDraft("order", definition)
        service.publish("order", 1)
        service.createDraft("order", definition)
        service.publish("order", 2)
        service.createDraft("order", definition)
        assertThatThrownBy { service.deleteDraft("order", 1) }.isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { service.deleteDraft("order", 2) }.isInstanceOf(IllegalStateException::class.java)
        service.deleteDraft("order", 3)
        assertThat(service.list("order")).hasSize(2)
        assertThatThrownBy { service.deleteDraft("order", 3) }.isInstanceOf(NoSuchElementException::class.java)
        assertThat(service.createDraft("order", definition).version).isEqualTo(3)
        assertThatThrownBy { store.unsupportedDelete() }.isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test
    fun `rejects invalid keys before storage`() {
        listOf("", "a/../b", "a".repeat(121)).forEach { key ->
            assertThatThrownBy { service.createDraft(key, definition) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
        assertThatThrownBy { service.publish(null, 1) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(store.locks).isZero()
    }

    private class TestStore : FlowStore {
        private val flows = mutableMapOf<String, MutableMap<Int, FlowVersion>>()
        fun unsupportedDelete() = super.deleteDraft("order", 1)
        override fun deleteDraft(flowKey: String, version: Int) { flows[flowKey]?.remove(version) }
        var locks = 0
            private set

        override fun lock(flowKey: String) {
            locks++
            flows.getOrPut(flowKey) { mutableMapOf() }
        }

        override fun latest(flowKey: String): Optional<FlowVersion> =
            Optional.ofNullable(flows[flowKey]?.values?.maxByOrNull(FlowVersion::version))

        override fun get(flowKey: String, version: Int): Optional<FlowVersion> =
            Optional.ofNullable(flows[flowKey]?.get(version))

        override fun active(flowKey: String): Optional<FlowVersion> =
            Optional.ofNullable(list(flowKey).firstOrNull { it.status == FlowVersionStatus.ACTIVE })

        override fun list(flowKey: String): List<FlowVersion> = flows[flowKey]?.values.orEmpty().toList()

        override fun save(version: FlowVersion): FlowVersion {
            flows.getValue(version.flowKey)[version.version] = version
            return version
        }
    }
}
