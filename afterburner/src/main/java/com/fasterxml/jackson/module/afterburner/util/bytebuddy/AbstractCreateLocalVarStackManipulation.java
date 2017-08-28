package com.fasterxml.jackson.module.afterburner.util.bytebuddy;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.assign.TypeCasting;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import net.bytebuddy.jar.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds bytecode for operation like:
 * <pre>
 * {@code
 * Bean varX = (Bean)var1;
 * }
 * <pre/>
 *
 * The BytecodeAppender that adds this StackManipulation needs to manually increment the number of
 * local variables by one
 */
public abstract class AbstractCreateLocalVarStackManipulation extends AbstractPropertyStackManipulation {
    private final TypeDescription beanClassDescription;

    public AbstractCreateLocalVarStackManipulation(
            TypeDescription beanClassDescription,
            AbstractPropertyStackManipulation.LocalVarIndexCalculator localVarIndexCalculator) {
        super(localVarIndexCalculator);
        this.beanClassDescription = beanClassDescription;
    }

    @Override
    public Size apply(MethodVisitor methodVisitor,
                      Implementation.Context implementationContext) {

        final List<StackManipulation> operations = new ArrayList<StackManipulation>();

        operations.add(MethodVariableAccess.REFERENCE.loadFrom(beanArgIndex())); //load the bean
        operations.add(TypeCasting.to(beanClassDescription));

        operations.add(MethodVariableAccess.REFERENCE.storeAt(localVarIndexCalculator.calculate()));

        final StackManipulation.Compound compound = new StackManipulation.Compound(operations);
        return compound.apply(methodVisitor, implementationContext);
    }
}
