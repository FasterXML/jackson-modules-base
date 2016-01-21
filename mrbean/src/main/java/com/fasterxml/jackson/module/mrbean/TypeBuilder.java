package com.fasterxml.jackson.module.mrbean;

import com.fasterxml.jackson.databind.JavaType;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;

import static org.objectweb.asm.Opcodes.*;

/**
 * Asm build to generate abstract type
 *
 * @author Eugene Petrenko (eugene.petrenko@jetbrains.com)
 */
public class TypeBuilder {

    protected final JavaType _implementedType;

    public TypeBuilder(JavaType type) {
        _implementedType = type;
    }

    /**
     * Method that generates byte code for class that implements abstract
     * types requested so far.
     *
     * @param className Fully-qualified name of the class to generate
     * @return Byte code Class instance built by this builder
     */
    public byte[] buildAbstractBase(String className)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String internalClass = getInternalClassName(className);
        String implName = getInternalClassName(_implementedType.getRawClass().getName());

        // muchos important: level at least 1.5 to get generics!!!
        // Also: abstract class vs interface...
        String superName;
        if (_implementedType.isInterface()) {
            superName = getInternalClassName(Object.class.getName());

            SignatureWriter sw = new SignatureWriter();
            final SignatureVisitor sv = sw.visitSuperclass();
            sv.visitClassType(superName);
            sv.visitEnd();

            generateSignature(_implementedType, sw.visitInterface());
            sw.visitEnd();

            cw.visit(V1_5, ACC_ABSTRACT + ACC_PUBLIC + ACC_SUPER, internalClass, sw.toString(),
                    superName, new String[] { implName });
        } else {
            superName = implName;

            SignatureWriter sw = new SignatureWriter();
            generateSignature(_implementedType, sw.visitSuperclass());

            sw.visitEnd();

            cw.visit(V1_5, ACC_ABSTRACT + ACC_PUBLIC + ACC_SUPER, internalClass, sw.toString(),
                    superName, null);
        }
        cw.visitSource(className + ".java", null);
        BeanBuilder.generateDefaultConstructor(cw, superName);
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static String getInternalClassName(String className) {
        return className.replace(".", "/");
    }

    private static void generateSignature(JavaType clazz, SignatureVisitor sw) {
        sw.visitClassType(getInternalClassName(clazz.getRawClass().getName()));
        if (clazz.containedTypeCount() ==  0) {
            return;
        }

        sw.visitTypeArgument('=');
        for(int i = 0; i < clazz.containedTypeCount(); i++) {
            final JavaType type = clazz.containedType(i);
            generateSignature(type, sw);

        }
        sw.visitEnd();
    }
}
