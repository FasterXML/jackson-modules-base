package com.fasterxml.jackson.module.afterburner.util.bytebuddy;

import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.constant.IntegerConstant;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides template for a method that invokes an operation on a bean. The method or field
 * that the operation is performed on is chosen by the supplied index and the resulting bytecode
 * uses jump to simulate if/then/else statements
 *
 * The invocation on the bean is configurable via a {@link SinglePropStackManipulationSupplier}
 *
 * One possible bytecode outcome would be:
 * <pre>
 * {@code
 *  if (var2 == 0) {
 *      varX.setA(var3);
 *  } else if (var2 == 1) {
 *      varX.setB(var3);
 *  } else {
 *      varX.setC(var3);
 *  }
 * }
 * </pre>
 *
 * Another possible bytecode outcome this class could produce would be:
 * <pre>
 * {@code
 *  if (var2 == 0) {
 *      return varX.getA();
 *  } else if (var2 == 1) {
 *      return varX.getB();
 *  } else {
 *      return varX.getC();
 *  }
 * }
 * </pre>
 */
public class UsingIfStackManipulation<T> extends AbstractPropertyStackManipulation {

    private final List<T> props;
    private final SinglePropStackManipulationSupplier<T> singlePropStackManipulationSupplier;

    public UsingIfStackManipulation(LocalVarIndexCalculator localVarIndexCalculator,
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

        Label next = new Label();
        //check if 'index == 0'
        stackManipulations.add(JumpStackManipulation.ifne(next));

        //first field accessor
        stackManipulations.add(singlePropStackManipulationSupplier.supply(props.get(0)));

        //loop to create accessors for the rest of the fields
        for (int i = 1, end = props.size()-1; i <= end; ++i) {
            stackManipulations.add(new VisitLabelStackManipulation(next));
            // No comparison needed for the last entry; assumed to match
            if (i < end) {
                next = new Label();
                stackManipulations.add(loadFieldIndexArg());
                stackManipulations.add(IntegerConstant.forValue(i));
                stackManipulations.add(JumpStackManipulation.if_icmpne(next));
            }

            stackManipulations.add(singlePropStackManipulationSupplier.supply(props.get(i)));
        }

        final StackManipulation.Compound compound = new StackManipulation.Compound(stackManipulations);
        return compound.apply(methodVisitor, implementationContext);
    }

}
