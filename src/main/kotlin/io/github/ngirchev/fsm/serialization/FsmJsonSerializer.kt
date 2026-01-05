package io.github.ngirchev.fsm.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import java.io.InputStream
import java.io.OutputStream

/**
 * JSON serializer for FSM transition tables
 */
class FsmJsonSerializer(
    val objectMapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
) {
    
    /**
     * Serializes ExTransitionTable to JSON string
     */
    fun <STATE, EVENT> serialize(transitionTable: ExTransitionTable<STATE, EVENT>): String {
        val dto = transitionTable.toDto()
        return objectMapper.writeValueAsString(dto)
    }
    
    /**
     * Serializes ExTransitionTable to JSON and writes to OutputStream
     */
    fun <STATE, EVENT> serialize(transitionTable: ExTransitionTable<STATE, EVENT>, output: OutputStream) {
        val dto = transitionTable.toDto()
        objectMapper.writeValue(output, dto)
    }
    
    /**
     * Deserializes JSON string to FsmDto
     */
    fun deserializeDto(json: String): FsmDto {
        return objectMapper.readValue(json, FsmDto::class.java)
    }
    
    /**
     * Deserializes JSON from InputStream to FsmDto
     */
    fun deserializeDto(input: InputStream): FsmDto {
        return objectMapper.readValue(input, FsmDto::class.java)
    }
    
    /**
     * Deserializes JSON string to ExTransitionTable
     * Requires state and event parsers and optional factories for actions/guards
     */
    fun <STATE, EVENT> deserialize(
        json: String,
        stateParser: (String) -> STATE,
        eventParser: (String) -> EVENT,
        actionFactory: ActionFactory<STATE>? = null,
        guardFactory: GuardFactory<STATE>? = null
    ): ExTransitionTable<STATE, EVENT> {
        val dto = deserializeDto(json)
        return dto.toExTransitionTable(stateParser, eventParser, actionFactory, guardFactory)
    }
    
    /**
     * Deserializes JSON from InputStream to ExTransitionTable
     */
    fun <STATE, EVENT> deserialize(
        input: InputStream,
        stateParser: (String) -> STATE,
        eventParser: (String) -> EVENT,
        actionFactory: ActionFactory<STATE>? = null,
        guardFactory: GuardFactory<STATE>? = null
    ): ExTransitionTable<STATE, EVENT> {
        val dto = deserializeDto(input)
        return dto.toExTransitionTable(stateParser, eventParser, actionFactory, guardFactory)
    }
}
