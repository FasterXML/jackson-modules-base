package com.fasterxml.jackson.module.afterburner.util;


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

    protected static String internalClassName(String className) {
        return className.replace(".", "/");
    }
    
    protected <T> T _add(List<T> list, T value) {
        list.add(value);
        ++_accessorCount;
        return value;
    }

}
