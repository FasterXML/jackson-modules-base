package com.fasterxml.jackson.module.afterburner.deser;

import static org.objectweb.asm.Opcodes.*;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.std.StdValueInstantiator;
import com.fasterxml.jackson.databind.introspect.AnnotatedWithParams;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import com.fasterxml.jackson.module.afterburner.util.ClassName;
import com.fasterxml.jackson.module.afterburner.util.DynamicPropertyAccessorBase;
import com.fasterxml.jackson.module.afterburner.util.MyClassLoader;

/**
 * Helper class that tries to generate {@link ValueInstantiator} class
 * that calls constructors and/or factory methods directly, instead
 * of using Reflection.
 */
public class CreatorOptimizer
    extends DynamicPropertyAccessorBase
{
    protected final Class<?> _valueClass;
    
    protected final MyClassLoader _classLoader;
    
    protected final StdValueInstantiator _originalInstantiator;

    public CreatorOptimizer(Class<?> valueClass, MyClassLoader classLoader,
            StdValueInstantiator orig)
    {
        _valueClass = valueClass;
        _classLoader = classLoader;
        _originalInstantiator = orig;
    }

    public ValueInstantiator createOptimized()
    {
        /* [Issue#11]: Need to avoid optimizing if we use delegate- or
         *  property-based creators.
         */
        if (_originalInstantiator.canCreateFromObjectWith()
                || _originalInstantiator.canCreateUsingDelegate()) {
            return null;
        }
        
        // for now, only consider need to handle default creator
        AnnotatedWithParams defaultCreator = _originalInstantiator.getDefaultCreator();
        if (defaultCreator != null) {
            AnnotatedElement elem = defaultCreator.getAnnotated();
            if (elem instanceof Constructor<?>) {
                // First things first: as per [Issue#34], can NOT access private ctors or methods
                Constructor<?> ctor = (Constructor<?>) elem;
                if (!Modifier.isPrivate(ctor.getModifiers())) {
                    return createSubclass(ctor, null).with(_originalInstantiator);
                }
            } else if (elem instanceof Method) {
                Method m = (Method) elem;
                int mods = m.getModifiers();
                // and as above, can't access private ones
                if (Modifier.isStatic(mods) && !Modifier.isPrivate(mods)) {
                    return createSubclass(null, m).with(_originalInstantiator);
                }
            }
        }
        return null;
    }

    protected OptimizedValueInstantiator createSubclass(Constructor<?> ctor, Method factory)
    {
        MyClassLoader loader = (_classLoader == null) ?
            new MyClassLoader(_valueClass.getClassLoader(), true) : _classLoader;
        final ClassName baseName = ClassName.constructFor(_valueClass, "$Creator4JacksonDeserializer");

        // We need to know checksum even for lookups, so generate it first
        final byte[] bytecode = generateOptimized(baseName, ctor, factory);
        baseName.assignChecksum(bytecode);

        Class<?> impl = null;
        try {
            impl = loader.loadClass(baseName.getDottedName());
        } catch (ClassNotFoundException e) { }
        if (impl == null) {
            impl = loader.loadAndResolve(baseName, bytecode);
        }
        try {
            return (OptimizedValueInstantiator) impl.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate accessor class '"+baseName+"': "+e.getMessage(), e);
        }
    }

    protected byte[] generateOptimized(ClassName baseName, Constructor<?> ctor, Method factory)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String superClass = internalClassName(OptimizedValueInstantiator.class.getName());
        final String tmpClassName = baseName.getSlashedTemplate();

        cw.visit(V1_5, ACC_PUBLIC + ACC_SUPER + ACC_FINAL, tmpClassName, null, superClass, null);
        cw.visitSource(baseName.getSourceFilename(), null);

        // First: must define 2 constructors:
        // (a) default constructor, for creating bogus instance (just calls default instance)
        // (b) copy-constructor which takes StdValueInstantiator instance, passes to superclass
        final String optimizedValueInstDesc = Type.getDescriptor(OptimizedValueInstantiator.class);
        final String stdValueInstDesc = Type.getDescriptor(StdValueInstantiator.class);

        // default (no-arg) constructor:
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, superClass, "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        // then single-arg constructor
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "("+stdValueInstDesc+")V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKESPECIAL, superClass, "<init>", "("+stdValueInstDesc+")V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // and then non-static factory method to use second constructor (implements base-class method)
        // protected abstract OptimizedValueInstantiator with(StdValueInstantiator src);
        mv = cw.visitMethod(ACC_PUBLIC, "with", "("
                +stdValueInstDesc+")"+optimizedValueInstDesc, null, null);
        mv.visitCode();
        mv.visitTypeInsn(NEW, tmpClassName);
        mv.visitInsn(DUP);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKESPECIAL, tmpClassName, "<init>", "("+stdValueInstDesc+")V", false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // And then override: public Object createUsingDefault()
        mv = cw.visitMethod(ACC_PUBLIC, "createUsingDefault", "(" +
        		Type.getDescriptor(DeserializationContext.class)+")Ljava/lang/Object;", null, null);
        mv.visitCode();

        if (ctor != null) {
            addCreator(mv, ctor);
        } else {
            addCreator(mv, factory);
        }
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    protected void addCreator(MethodVisitor mv, Constructor<?> ctor)
    {
        Class<?> owner = ctor.getDeclaringClass();
        String valueClassInternal = Type.getInternalName(owner);
        mv.visitTypeInsn(NEW, valueClassInternal);
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, valueClassInternal, "<init>", "()V",
                owner.isInterface());
    }

    protected void addCreator(MethodVisitor mv, Method factory)
    {
        Class<?> owner = factory.getDeclaringClass();
        Class<?> valueClass = factory.getReturnType();
        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(owner),
                factory.getName(), "()"+Type.getDescriptor(valueClass),
                owner.isInterface());
    }
}
