package com.fasterxml.jackson.module.afterburner.util.bytebuddy;

import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides template for a method that invokes an operation on a bean. The method or field
 * that the operation is performed on is chosen by the supplied index and the resulting bytecode
 * uses tableswitch to simulate a switch statement
 *
 * The invocation on the bean is configurable via a {@link SinglePropStackManipulationSupplier}
 *
 * One possible bytecode outcome would be:
 * <pre>
 * {@code
 *   switch(var2) {
 *   case 0:
 *      var4.setC(var3);
 *      return;
 *   case 1:
 *      var4.setA(var3);
 *      return;
 *   case 2:
 *      var4.setB(var3);
 *      return;
 *   case 3:
 *      var4.setE(var3);
 *      return;
 *   default:
 * }
 * }
 * </pre>
 *
 * Another possible bytecode outcome this class could produce would be:
 * <pre>
 * {@code
 *   switch(var2) {
 *   case 0:
 *      return var4.getA();
 *   case 1:
 *      return var4.getB();
 *   case 2:
 *      return var4.getC();
 *   case 3:
 *      return var4.getD();
 *   default:
 * }
 * }
 * </pre>
 */
public class UsingSwitchStackManipulation<T> extends AbstractPropertyStackManipulation {

    private final List<T> props;
    private final SinglePropStackManipulationSupplier<T> singlePropStackManipulationSupplier;

    public UsingSwitchStackManipulation(LocalVarIndexCalculator localVarIndexCalculator,
                                        List<T> props,
                                        SinglePropStackManipulationSupplier<T> singlePropStackManipulationSupplier) {
        super(localVarIndexCalculator);
        this.props = props;
        this.singlePropStackManipulationSupplier = singlePropStackManipulationSupplier;
    }

    @Override
    public Size apply(MethodVisitor methodVisitor, Implementation.Context implementationContext) {
        final List<StackManipulation> stackManipulations = new ArrayList<StackManipulation>();

        stackManipulations.add(loadFieldIndexArg());

        final Label[] labels = new Label[props.size()];
        for (int i = 0, len = labels.length; i < len; ++i) {
            labels[i] = new Label();
        }
        final Label defaultLabel = new Label();
        stackManipulations.add(new TableSwitchStackManipulation(labels, defaultLabel));

        for (int i = 0, len = labels.length; i < len; ++i) {
            stackManipulations.add(new VisitLabelStackManipulation(labels[i]));
            stackManipulations.add(singlePropStackManipulationSupplier.supply(props.get(i)));
        }
        stackManipulations.add(new VisitLabelStackManipulation(defaultLabel));
        // and if no match, generate exception:
        stackManipulations.add(new GenerateIllegalPropertyCountExceptionStackManipulation(props.size()));

        final StackManipulation.Compound compound = new StackManipulation.Compound(stackManipulations);
        return compound.apply(methodVisitor, implementationContext);
    }
}
