package com.fasterxml.jackson.module.afterburner.util.bytebuddy;

import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;

import java.util.Arrays;

public final class TableSwitchStackManipulation implements StackManipulation {

    private final Label[] labels;
    private final Label defaultLabel;

    public TableSwitchStackManipulation(Label[] labels, Label defaultLabel) {
        this.labels = labels;
        this.defaultLabel = defaultLabel;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public Size apply(MethodVisitor methodVisitor, Implementation.Context implementationContext) {
        methodVisitor.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);
        return new Size(-1, 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TableSwitchStackManipulation that = (TableSwitchStackManipulation) o;

        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(labels, that.labels)) return false;
        return defaultLabel.equals(that.defaultLabel);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(labels);
        result = 31 * result + defaultLabel.hashCode();
        return result;
    }
}
