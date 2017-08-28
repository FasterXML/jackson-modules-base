package com.fasterxml.jackson.module.afterburner.util.bytebuddy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.jar.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides the basic algorithm for adding the starting and ending parts of method implementations
 * as well as handling the delegation to the proper appender
 * Should not be called when no properties are needed
 */
public abstract class AbstractDelegatingAppender<T> implements ByteCodeAppender {

    private final int propsSize;

    public AbstractDelegatingAppender(List<T> props) {
        this.propsSize = props.size();
    }

    /*
     * The following methods should use the fields of the class in order to instantiate the proper type
     */
    abstract protected StackManipulation createLocalVar();
    abstract protected StackManipulation usingSwitch();
    abstract protected StackManipulation usingIf();
    abstract protected StackManipulation single();

    @Override
    public Size apply(MethodVisitor methodVisitor,
                      Implementation.Context implementationContext,
                      MethodDescription instrumentedMethod) {

        final List<StackManipulation> stackManipulations = new ArrayList<StackManipulation>();
        //contains the initial bytecode needed for all cases
        stackManipulations.add(createLocalVar());

        // Ok; minor optimization, 3 or fewer fields, just do IFs; over that, use switch
        switch (propsSize) {
            case 1:
                stackManipulations.add(single());
                break;
            case 2:
            case 3:
                stackManipulations.add(usingIf());
                break;
            default:
                stackManipulations.add(usingSwitch());
        }

        final StackManipulation.Compound compound = new StackManipulation.Compound(stackManipulations);
        final StackManipulation.Size operandStackSize = compound.apply(methodVisitor, implementationContext);
        return new Size(operandStackSize.getMaximalSize(), instrumentedMethod.getStackSize() + 1);
    }
}
