package com.fasterxml.jackson.module.afterburner.util;


import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

public class DynamicPropertyAccessorBase
{
    protected final static int[] ALL_INT_CONSTS = new int[] {
        ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4
    };

    protected int _accessorCount = 0;

    protected DynamicPropertyAccessorBase() {
    }

    public final boolean isEmpty() {
        return (_accessorCount == 0);
    }

    /*
    /**********************************************************
    /* Helper methods, generating common pieces
    /**********************************************************
     */
    
    protected static void generateException(MethodVisitor mv, String beanClass, int propertyCount)
    {
        mv.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
        mv.visitInsn(DUP);
        mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        mv.visitInsn(DUP);
        mv.visitLdcInsn("Invalid field index (valid; 0 <= n < "+propertyCount+"): ");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(ATHROW);
    }
    
    /*
    /**********************************************************
    /* Helper methods, other
    /**********************************************************
     */

    /**
     * @since 2.9.2
     */
    protected static boolean isInterfaceMethod(Method method) {
        // 15-Sep-2017, tatu: As per [modules-base#30], Java 8 default methods need to be called as
        //   non-interface methods (since they generate real concrete methods). So further checks
        //   needed, not just that they are declared in an interface

        // Forward compatible with Java 8 equivalent: method.getDeclaringClass().isInterface() && !method.isDefault()
        // NOTE: `ABSTRACT` is really the key here: only "pure" interface methods abstract; default ones not.
        return method.getDeclaringClass().isInterface() &&
                ((method.getModifiers() & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC)) != Modifier.PUBLIC);
    }

    protected static String internalClassName(String className) {
        return className.replace(".", "/");
    }
    
    protected <T> T _add(List<T> list, T value) {
        list.add(value);
        ++_accessorCount;
        return value;
    }
}
