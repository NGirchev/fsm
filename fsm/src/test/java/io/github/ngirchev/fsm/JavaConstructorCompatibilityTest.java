package io.github.ngirchev.fsm;

import io.github.ngirchev.fsm.impl.basic.BDomainFsm;
import io.github.ngirchev.fsm.impl.basic.BFsm;
import io.github.ngirchev.fsm.impl.basic.BTransitionTable;
import io.github.ngirchev.fsm.impl.extended.ExDomainFsm;
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class JavaConstructorCompatibilityTest {

    @Test
    void oldNullableBooleanNullConstructorsShouldCompileWithoutAmbiguousOverloads() {
        BTransitionTable<String> basicTable = new BTransitionTable.Builder<String>().build();
        StateContext<String> context = new TestStateContext("initial");
        ExTransitionTable<String, String> extendedTable = new ExTransitionTable.Builder<String, String>().build();

        assertNotNull(new BFsm<>("initial", basicTable, null));
        assertNotNull(new BFsm<>(context, basicTable, null));
        assertNotNull(new BDomainFsm<StateContext<String>, String>(basicTable, null));
        assertNotNull(new ExDomainFsm<StateContext<String>, String, String>(extendedTable, null));
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
