package io.github.ngirchev.fsm.example.flow

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

enum class FlowVersionStatus { DRAFT, ACTIVE, ARCHIVED }

data class FlowVersion(
    val flowKey: String,
    val version: Int,
    val status: FlowVersionStatus,
    val definition: FlowDefinition,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val publishedAt: OffsetDateTime?,
)

class FlowNotFoundException(message: String) : NoSuchElementException(message)
class FlowConflictException(message: String) : IllegalStateException(message)

@Repository
class FlowRepository(
    private val jdbc: JdbcClient,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun createDraft(flowKey: String, definition: FlowDefinition): FlowVersion {
        jdbc.sql("INSERT INTO fsm_flow(flow_key) VALUES (:flowKey) ON CONFLICT (flow_key) DO NOTHING")
            .param("flowKey", flowKey)
            .update()
        val flowId = lockFlow(flowKey)
        val nextVersion = jdbc.sql("SELECT COALESCE(MAX(version), 0) + 1 FROM fsm_flow_version WHERE flow_id = :flowId")
            .param("flowId", flowId)
            .query(Int::class.java)
            .single()
        jdbc.sql(
            """
            INSERT INTO fsm_flow_version(flow_id, version, status, definition)
            VALUES (:flowId, :version, 'DRAFT', CAST(:definition AS jsonb))
            """.trimIndent(),
        )
            .param("flowId", flowId)
            .param("version", nextVersion)
            .param("definition", objectMapper.writeValueAsString(definition))
            .update()
        return get(flowKey, nextVersion)
    }

    @Transactional
    fun updateDraft(flowKey: String, version: Int, definition: FlowDefinition): FlowVersion {
        lockFlow(flowKey)
        val changed = jdbc.sql(
            """
            UPDATE fsm_flow_version v
            SET definition = CAST(:definition AS jsonb), updated_at = now()
            FROM fsm_flow f
            WHERE v.flow_id = f.id AND f.flow_key = :flowKey
              AND v.version = :version AND v.status = 'DRAFT'
            """.trimIndent(),
        )
            .param("definition", objectMapper.writeValueAsString(definition))
            .param("flowKey", flowKey)
            .param("version", version)
            .update()
        if (changed == 0) throw FlowConflictException("Only an existing draft version can be changed")
        return get(flowKey, version)
    }

    fun list(flowKey: String): List<FlowVersion> = jdbc.sql(BASE_SELECT + " WHERE f.flow_key = :flowKey ORDER BY v.version DESC")
        .param("flowKey", flowKey)
        .query(::mapVersion)
        .list()

    fun get(flowKey: String, version: Int): FlowVersion = jdbc.sql(
        BASE_SELECT + " WHERE f.flow_key = :flowKey AND v.version = :version",
    )
        .param("flowKey", flowKey)
        .param("version", version)
        .query(::mapVersion)
        .optional()
        .orElseThrow { FlowNotFoundException("Flow $flowKey version $version was not found") }

    fun active(flowKey: String): FlowVersion = jdbc.sql(
        BASE_SELECT + " WHERE f.flow_key = :flowKey AND v.id = f.active_version_id",
    )
        .param("flowKey", flowKey)
        .query(::mapVersion)
        .optional()
        .orElseThrow { FlowNotFoundException("Flow $flowKey has no active version") }

    @Transactional
    fun activate(flowKey: String, version: Int, validate: (FlowVersion) -> Unit): FlowVersion {
        val flowId = lockFlow(flowKey)
        val target = get(flowKey, version)
        if (target.status != FlowVersionStatus.DRAFT) {
            throw FlowConflictException("Only a draft version can be published")
        }
        validate(target)
        jdbc.sql("UPDATE fsm_flow_version SET status = 'ARCHIVED', updated_at = now() WHERE flow_id = :flowId AND status = 'ACTIVE'")
            .param("flowId", flowId)
            .update()
        jdbc.sql(
            "UPDATE fsm_flow_version SET status = 'ACTIVE', published_at = now(), updated_at = now() WHERE flow_id = :flowId AND version = :version",
        )
            .param("flowId", flowId)
            .param("version", version)
            .update()
        jdbc.sql(
            "UPDATE fsm_flow SET active_version_id = (SELECT id FROM fsm_flow_version WHERE flow_id = :flowId AND version = :version) WHERE id = :flowId",
        )
            .param("flowId", flowId)
            .param("version", version)
            .update()
        return get(flowKey, version)
    }

    private fun lockFlow(flowKey: String): Long = try {
        jdbc.sql("SELECT id FROM fsm_flow WHERE flow_key = :flowKey FOR UPDATE")
            .param("flowKey", flowKey)
            .query(Long::class.java)
            .single()
    } catch (_: EmptyResultDataAccessException) {
        throw FlowNotFoundException("Flow $flowKey was not found")
    }

    private fun mapVersion(rs: java.sql.ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): FlowVersion = FlowVersion(
        flowKey = rs.getString("flow_key"),
        version = rs.getInt("version"),
        status = FlowVersionStatus.valueOf(rs.getString("status")),
        definition = objectMapper.readValue(rs.getString("definition"), FlowDefinition::class.java),
        createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
        updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
        publishedAt = rs.getObject("published_at", OffsetDateTime::class.java),
    )

    companion object {
        private const val BASE_SELECT = """
            SELECT f.flow_key, v.version, v.status, v.definition,
                   v.created_at, v.updated_at, v.published_at
            FROM fsm_flow_version v
            JOIN fsm_flow f ON f.id = v.flow_id
        """
    }
}
