package com.fasterxml.jackson.module.afterburner.util.bytebuddy;

import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;

public final class VisitLabelStackManipulation implements StackManipulation {

    private final Label label;

    public VisitLabelStackManipulation(Label label) {
        this.label = label;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public Size apply(MethodVisitor methodVisitor, Implementation.Context implementationContext) {
        methodVisitor.visitLabel(label);
        return new Size(0, 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VisitLabelStackManipulation that = (VisitLabelStackManipulation) o;

        return label.equals(that.label);
    }

    @Override
    public int hashCode() {
        return label.hashCode();
    }
}
