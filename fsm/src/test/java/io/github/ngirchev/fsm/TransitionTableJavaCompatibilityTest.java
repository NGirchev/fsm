package io.github.ngirchev.fsm;

import io.github.ngirchev.fsm.impl.basic.BTransition;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransitionTableJavaCompatibilityTest {

    @Test
    void defaultJavaImplementationWithoutOverridesUsesInterfaceDefaults() {
        DefaultJavaTransitionTable table = new DefaultJavaTransitionTable();
        TestStateContext context = new TestStateContext("from");

        assertFalse(table.getAutoTransitionEnabled());
        assertNull(table.getAutoTransition(context));
        assertNull(table.getAutoTransition(context, true));
        assertNull(table.getAutoTransition(context, false));
    }

    @Test
    void legacyJavaImplementationCanOverrideOneArgMethodOnly() {
        LegacyJavaTransitionTable table = new LegacyJavaTransitionTable();
        TestStateContext context = new TestStateContext("from");

        assertNotNull(table.getAutoTransition(context));
        assertNotNull(table.getAutoTransition(context, true));
        assertNull(table.getAutoTransition(context, false));
    }

    @Test
    void newStyleJavaImplementationCanOverrideTwoArgMethodOnly() {
        NewStyleJavaTransitionTable table = new NewStyleJavaTransitionTable();
        TestStateContext context = new TestStateContext("from");

        assertTrue(table.getAutoTransitionEnabled());
        assertNotNull(table.getAutoTransition(context));
        assertNotNull(table.getAutoTransition(context, true));
        assertNull(table.getAutoTransition(context, false));
    }

    private static final class DefaultJavaTransitionTable implements TransitionTable<String, BTransition<String>> {
        @Override
        public Map<String, LinkedHashSet<? extends BTransition<String>>> getTransitions() {
            return Collections.emptyMap();
        }

        @Override
        public BTransition<String> getTransitionByState(StateContext<String> context, String newState) {
            return null;
        }

        @Override
        public StateSupport<String> createFsm(String initialState) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <DOMAIN extends StateContext<String>> DomainSupport<DOMAIN, String> createDomainFsm() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class LegacyJavaTransitionTable implements TransitionTable<String, BTransition<String>> {
        @Override
        public Map<String, LinkedHashSet<? extends BTransition<String>>> getTransitions() {
            return Collections.emptyMap();
        }

        @Override
        public BTransition<String> getTransitionByState(StateContext<String> context, String newState) {
            return null;
        }

        @Override
        public BTransition<String> getAutoTransition(StateContext<String> context) {
            return new BTransition<>("from", "to");
        }

        @Override
        public StateSupport<String> createFsm(String initialState) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <DOMAIN extends StateContext<String>> DomainSupport<DOMAIN, String> createDomainFsm() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class NewStyleJavaTransitionTable implements TransitionTable<String, BTransition<String>> {
        @Override
        public Map<String, LinkedHashSet<? extends BTransition<String>>> getTransitions() {
            return Collections.emptyMap();
        }

        @Override
        public boolean getAutoTransitionEnabled() {
            return true;
        }

        @Override
        public BTransition<String> getTransitionByState(StateContext<String> context, String newState) {
            return null;
        }

        @Override
        public BTransition<String> getAutoTransition(StateContext<String> context, boolean autoTransitionEnabled) {
            return autoTransitionEnabled ? new BTransition<>("from", "to") : null;
        }

        @Override
        public StateSupport<String> createFsm(String initialState) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <DOMAIN extends StateContext<String>> DomainSupport<DOMAIN, String> createDomainFsm() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TestStateContext implements StateContext<String> {
        private String state;
        private Transition<String> currentTransition;

        private TestStateContext(String state) {
            this.state = state;
        }

        @Override
        public String getState() {
            return state;
        }

        @Override
        public void setState(String state) {
            this.state = state;
        }

        @Override
        public Transition<String> getCurrentTransition() {
            return currentTransition;
        }

        @Override
        public void setCurrentTransition(Transition<String> currentTransition) {
            this.currentTransition = currentTransition;
        }
    }
}
