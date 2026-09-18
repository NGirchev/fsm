package io.github.ngirchev.fsm.example.flow

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StoredFlowJsonTest {
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    @Test
    fun `reads existing seed without changing its stored JSON and preserves transition order`() {
        val seed = javaClass.getResource("/db/migration/V2__seed_order_flow.sql")!!.readText()
        val json = seed.substringAfter("\$json\$").substringBefore("\$json\$")
        val original = mapper.readTree(json)
        val definition = mapper.readFlowDefinition(json)
        assertEquals("NEW", definition.initialState)
        assertEquals(listOf("SUBMIT", "CANCEL"), definition.table.transitions.getValue("NEW").map { it.event })
        val payment = definition.table.transitions.getValue("PAYMENT_PENDING").first().to
        assertEquals(listOf("paymentApproved"), payment.conditions)
        assertEquals(listOf("capturePayment"), payment.actions)
        assertEquals(listOf("sendPaymentReceipt"), payment.postActions)
        assertEquals(emptyList(), definition.table.transitions.getValue("COMPLETED"))
        assertEquals(original, mapper.readTree(json))
    }

    @Test
    fun `new definitions round trip using the core table format`() {
        val json = javaClass.getResource("/db/migration/V2__seed_order_flow.sql")!!.readText()
            .substringAfter("\$json\$").substringBefore("\$json\$")
        val definition = mapper.readFlowDefinition(json)
        val restoredJson = mapper.writeValueAsString(definition)
        assertFalse(mapper.readTree(restoredJson).has("schemaVersion"))
        assertEquals(definition, mapper.readFlowDefinition(restoredJson))
    }
}
