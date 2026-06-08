package io.github.ngirchev.fsm

import io.github.ngirchev.fsm.impl.basic.BDomainFsm
import io.github.ngirchev.fsm.impl.basic.BFsm
import io.github.ngirchev.fsm.impl.basic.BTransitionTable
import io.github.ngirchev.fsm.impl.extended.ExDomainFsm
import io.github.ngirchev.fsm.impl.extended.ExFsm
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import kotlin.test.Test
import kotlin.test.assertNotNull

class PublicConstructorCompatibilityTest {

    @Test
    fun bFsmShouldKeepOldJvmConstructors() {
        assertNotNull(
            BFsm::class.java.getConstructor(
                Any::class.java,
                BTransitionTable::class.java,
                Boolean::class.javaObjectType,
            )
        )
        assertNotNull(
            BFsm::class.java.getConstructor(
                StateContext::class.java,
                BTransitionTable::class.java,
                Boolean::class.javaObjectType,
            )
        )
    }

    @Test
    fun bDomainFsmShouldKeepOldJvmConstructor() {
        assertNotNull(
            BDomainFsm::class.java.getConstructor(
                BTransitionTable::class.java,
                Boolean::class.javaObjectType,
            )
        )
    }

    @Test
    fun exFsmShouldKeepOldJvmConstructors() {
        assertNotNull(
            ExFsm::class.java.getConstructor(
                Any::class.java,
                ExTransitionTable::class.java,
                Boolean::class.javaPrimitiveType,
            )
        )
        assertNotNull(
            ExFsm::class.java.getConstructor(
                StateContext::class.java,
                ExTransitionTable::class.java,
                Boolean::class.javaPrimitiveType,
            )
        )
    }

    @Test
    fun exDomainFsmShouldKeepOldJvmConstructor() {
        assertNotNull(
            ExDomainFsm::class.java.getConstructor(
                ExTransitionTable::class.java,
                Boolean::class.javaObjectType,
            )
        )
    }
}
