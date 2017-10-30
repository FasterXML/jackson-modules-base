package com.fasterxml.jackson.module.afterburner.util.bytebuddy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;

/**
 * Provides a simple abstraction for implementing
 * <pre>
 * {@code
 * try {
 *   //code
 * } catch(SomeException e) {
 *   //code
 * }
 * }
 * <pre/>
 *
 * exceptionThrowingAppender MUST end with a return statement
 *
 * Due to way the JVM handles exceptions, when exceptionHandlerAppender is run,
 * the exception can be found on the top of the stack
 */
public class SimpleExceptionHandler implements Implementation, ByteCodeAppender {

    private final StackManipulation exceptionThrowingAppender;
    private final StackManipulation exceptionHandlerAppender;
    private final Class<? extends Exception> exceptionType;
    private final int newLocalVariablesCount;

    public SimpleExceptionHandler(StackManipulation exceptionThrowingAppender,
                                  StackManipulation exceptionHandlerAppender,
                                  Class<? extends Exception> exceptionType,
                                  int newLocalVariablesCount) {
        this.exceptionThrowingAppender = exceptionThrowingAppender;
        this.exceptionHandlerAppender = exceptionHandlerAppender;
        this.exceptionType = exceptionType;
        this.newLocalVariablesCount = newLocalVariablesCount;
    }

    @Override
    public InstrumentedType prepare(InstrumentedType instrumentedType) {
        return instrumentedType;
    }

    @Override
    public ByteCodeAppender appender(Target implementationTarget) {
        return this;
    }

    @Override
    public Size apply(MethodVisitor methodVisitor,
                      Implementation.Context implementationContext,
                      MethodDescription instrumentedMethod) {

        final Label startTryBlock = new Label();
        final Label endTryBlock = new Label();
        final Label startCatchBlock = new Label();

        final StackManipulation preTriggerAppender = preTrigger(startTryBlock, endTryBlock, startCatchBlock);
        final StackManipulation postTriggerAppender = postTrigger(endTryBlock, startCatchBlock);

        final StackManipulation delegate = new StackManipulation.Compound(
                preTriggerAppender, exceptionThrowingAppender, postTriggerAppender, exceptionHandlerAppender
        );

        final StackManipulation.Size operandStackSize = delegate.apply(methodVisitor, implementationContext);
        return new Size(operandStackSize.getMaximalSize(), instrumentedMethod.getStackSize() + newLocalVariablesCount);
    }

    private StackManipulation preTrigger(final Label startTryBlock,
                                         final Label endTryBlock,
                                         final Label startCatchBlock) {
        return new StackManipulation() {
            @Override
            public boolean isValid() {
                return true;
            }

            @Override
            public Size apply(MethodVisitor mv, Implementation.Context ic) {
                final String name = exceptionType.getName();
                mv.visitTryCatchBlock(startTryBlock, endTryBlock, startCatchBlock, name.replace(".", "/"));
                mv.visitLabel(startTryBlock);
                return new Size(0,0);
            }
        };
    }

    private StackManipulation postTrigger(final Label endTryBlock, final Label startCatchBlock) {
        return new StackManipulation() {
            @Override
            public boolean isValid() {
                return true;
            }

            @Override
            public Size apply(MethodVisitor mv, Implementation.Context ic) {
                mv.visitLabel(endTryBlock);
                // and then do catch block
                mv.visitLabel(startCatchBlock);

                //although this StackManipulation does not alter the stack on it's own
                //however when an exception is caught
                //the exception will be on the top of the stack (being placed there by the JVM)
                //we need to increment the stack size
                return new Size(1, 0);
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SimpleExceptionHandler that = (SimpleExceptionHandler) o;

        if (!exceptionThrowingAppender.equals(that.exceptionThrowingAppender)) return false;
        if (!exceptionHandlerAppender.equals(that.exceptionHandlerAppender)) return false;
        return exceptionType.equals(that.exceptionType);
    }

    @Override
    public int hashCode() {
        int result = exceptionThrowingAppender.hashCode();
        result = 31 * result + exceptionHandlerAppender.hashCode();
        result = 31 * result + exceptionType.hashCode();
        return result;
    }
}
