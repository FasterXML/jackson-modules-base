package com.fasterxml.jackson.module.afterburner.util.bytebuddy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.Throw;
import net.bytebuddy.implementation.bytecode.constant.TextConstant;
import net.bytebuddy.jar.asm.MethodVisitor;

import static net.bytebuddy.description.type.TypeDescription.ForLoadedType;
import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * Generates the byte-code needed to throw an IllegalArgumentException with the corresponding helper message
 */
public final class GenerateIllegalPropertyCountExceptionStackManipulation implements StackManipulation {

    private final int propertyCount;

    public GenerateIllegalPropertyCountExceptionStackManipulation(int propertyCount) {
        this.propertyCount = propertyCount;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public Size apply(MethodVisitor methodVisitor, Implementation.Context implementationContext) {
        final MethodDescription.InDefinedShape messageConstructor =
                new ForLoadedType(IllegalArgumentException.class)
                        .getDeclaredMethods()
                        .filter(isConstructor())
                        .filter(takesArguments(1))
                        .filter(takesArgument(0, String.class))
                        .getOnly();

        final StackManipulation.Compound delegate = new StackManipulation.Compound(
                new ConstructorCallStackManipulation.KnownInDefinedShapeOfExistingType(
                        messageConstructor,
                        new TextConstant("Invalid field index (valid; 0 <= n < "+propertyCount+"): ")
                ),
                Throw.INSTANCE
        );

        return delegate.apply(methodVisitor, implementationContext);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GenerateIllegalPropertyCountExceptionStackManipulation that = (GenerateIllegalPropertyCountExceptionStackManipulation) o;

        return propertyCount == that.propertyCount;
    }

    @Override
    public int hashCode() {
        return propertyCount;
    }
}
