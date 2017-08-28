package com.fasterxml.jackson.module.afterburner.util.bytebuddy;

import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;

import static net.bytebuddy.jar.asm.Opcodes.IFNE;
import static net.bytebuddy.jar.asm.Opcodes.IF_ICMPNE;

public final class JumpStackManipulation implements StackManipulation {

    private final int opcde;
    private final int stackImpact;
    private final Label label;

    private JumpStackManipulation(int opcde, int stackImpact, Label label) {
        this.label = label;
        this.stackImpact = stackImpact;
        this.opcde = opcde;
    }

    public static JumpStackManipulation ifne(Label label) {
        return new JumpStackManipulation(IFNE, -1, label);
    }

    public static JumpStackManipulation if_icmpne(Label label) {
        return new JumpStackManipulation(IF_ICMPNE, -2, label);
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public Size apply(MethodVisitor methodVisitor, Implementation.Context implementationContext) {
        methodVisitor.visitJumpInsn(opcde, label);
        return new Size(stackImpact, 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JumpStackManipulation that = (JumpStackManipulation) o;

        if (opcde != that.opcde) return false;
        if (stackImpact != that.stackImpact) return false;
        return label.equals(that.label);
    }

    @Override
    public int hashCode() {
        int result = opcde;
        result = 31 * result + stackImpact;
        result = 31 * result + label.hashCode();
        return result;
    }
}
