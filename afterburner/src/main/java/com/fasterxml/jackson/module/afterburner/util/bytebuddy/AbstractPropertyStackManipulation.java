package com.fasterxml.jackson.module.afterburner.util.bytebuddy;

import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;

/**
 * Contains base methods that common between the code used in
 * {@link com.fasterxml.jackson.module.afterburner.deser.PropertyMutatorCollector} and
 * {@link com.fasterxml.jackson.module.afterburner.ser.PropertyAccessorCollector}
 */
public abstract class AbstractPropertyStackManipulation implements StackManipulation {

    protected final LocalVarIndexCalculator localVarIndexCalculator;

    public AbstractPropertyStackManipulation(LocalVarIndexCalculator localVarIndexCalculator) {
        this.localVarIndexCalculator = localVarIndexCalculator;
    }

    @Override
    public boolean isValid() {
        return false;
    }

    public final int beanArgIndex() {
        return 1;
    }

    public final int fieldIndexArgIndex() {
        return 2;
    }

    public final StackManipulation loadFieldIndexArg() {
        return MethodVariableAccess.INTEGER.loadFrom(fieldIndexArgIndex());
    }

    protected final int localVarIndex() {
        return localVarIndexCalculator.calculate();
    }

    public interface LocalVarIndexCalculator {

        int calculate();
    }
}
