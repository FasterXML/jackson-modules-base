package tools.jackson.module.afterburner.util.bytebuddy;

import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;

/**
 * Contains base methods that common between the code used in
 * {@link tools.jackson.module.afterburner.deser.PropertyMutatorCollector} and
 * {@link tools.jackson.module.afterburner.ser.PropertyAccessorCollector}
 */
public abstract class AbstractPropertyStackManipulation implements StackManipulation {

    protected final LocalVarIndexCalculator localVarIndexCalculator;
    private final boolean isForMutator;

    public AbstractPropertyStackManipulation(LocalVarIndexCalculator localVarIndexCalculator, boolean isForMutator) {
        this.localVarIndexCalculator = localVarIndexCalculator;
        this.isForMutator = isForMutator;
    }

    @Override
    public boolean isValid() {
        return false;
    }

    public final int beanArgIndex() {
        return 1 + (isForMutator ? 1 : 0);
    }

    public final int fieldIndexArgIndex() {
        return 2  + (isForMutator ? 1 : 0);
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
