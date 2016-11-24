package com.fasterxml.jackson.module.afterburner.deser;

import java.lang.reflect.Method;
import java.util.*;

import org.objectweb.asm.*;

import static org.objectweb.asm.Opcodes.*;

import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;

import com.fasterxml.jackson.module.afterburner.deser.impl.*;
import com.fasterxml.jackson.module.afterburner.util.ClassName;
import com.fasterxml.jackson.module.afterburner.util.DynamicPropertyAccessorBase;
import com.fasterxml.jackson.module.afterburner.util.MyClassLoader;

/**
 * Simple collector used to keep track of properties for which code-generated
 * mutators are needed.
 */
public class PropertyMutatorCollector
    extends DynamicPropertyAccessorBase
{
    private static final Type STRING_TYPE = Type.getType(String.class);
    private static final Type OBJECT_TYPE = Type.getType(Object.class);

    private static final int SPECIAL_SETTERS_BOOLEAN = 0; // since 2.9
    private static final int SPECIAL_SETTERS_INT = 4; // since 2.9
    private static final int SPECIAL_SETTERS_LONG = 2; // since 2.9
    private static final int SPECIAL_SETTERS_STRING = 4; // since 2.9
    private static final int SPECIAL_SETTERS_OBJECT = 2; // since 2.9

    private static final int SPECIAL_FIELDS_BOOLEAN = 0; // since 2.9
    private static final int SPECIAL_FIELDS_INT = 2; // since 2.9
    private static final int SPECIAL_FIELDS_LONG = 0; // since 2.9
    private static final int SPECIAL_FIELDS_STRING = 2; // since 2.9
    private static final int SPECIAL_FIELDS_OBJECT = 0; // since 2.9
    
    private final List<OptimizedSettableBeanProperty<?>> _intSetters = new LinkedList<>();
    private final List<OptimizedSettableBeanProperty<?>> _longSetters = new LinkedList<>();
    private final List<OptimizedSettableBeanProperty<?>> _booleanSetters = new LinkedList<>();
    private final List<OptimizedSettableBeanProperty<?>> _stringSetters = new LinkedList<>();
    private final List<OptimizedSettableBeanProperty<?>> _objectSetters = new LinkedList<>();

    private final List<OptimizedSettableBeanProperty<?>> _intFields = new LinkedList<>();
    private final List<OptimizedSettableBeanProperty<?>> _longFields = new LinkedList<>();
    private final List<OptimizedSettableBeanProperty<?>> _booleanFields = new LinkedList<>();
    private final List<OptimizedSettableBeanProperty<?>> _stringFields = new LinkedList<>();
    private final List<OptimizedSettableBeanProperty<?>> _objectFields = new LinkedList<>();

    private final Class<?> beanClass;
    private final String beanClassName;

    public PropertyMutatorCollector(Class<?> beanClass) {
        this.beanClass = beanClass;
        this.beanClassName = Type.getInternalName(beanClass);
    }
    
    /*
    /**********************************************************
    /* Methods for collecting properties
    /**********************************************************
     */

    public OptimizedSettableBeanProperty<?> addIntSetter(SettableBeanProperty prop) {
        int ix = _intSetters.size();
        OptimizedSettableBeanProperty<?> optProp;
        switch (ix) {
        case 0:
            optProp = new SettableInt0MethodProperty(prop, null, ix);
            break;
        case 1:
            optProp = new SettableInt1MethodProperty(prop, null, ix);
            break;
        case 2:
            optProp = new SettableInt2MethodProperty(prop, null, ix);
            break;
        case 3:
            optProp = new SettableInt3MethodProperty(prop, null, ix);
            break;
        default:
            optProp = new SettableIntMethodProperty(prop, null, ix-4);
        }
        return _add(_intSetters, optProp);
    }
    public OptimizedSettableBeanProperty<?> addLongSetter(SettableBeanProperty prop) {
        int ix = _longSetters.size();
        OptimizedSettableBeanProperty<?> optProp;
        switch (ix) {
        case 0:
            optProp = new SettableLong0MethodProperty(prop, null, ix);
            break;
        case 1:
            optProp = new SettableLong1MethodProperty(prop, null, ix);
            break;
        default:
            optProp = new SettableLongMethodProperty(prop, null, ix-2);
        }
        return _add(_longSetters, optProp);
    }
    public OptimizedSettableBeanProperty<?> addBooleanSetter(SettableBeanProperty prop) {
        return _add(_booleanSetters, new SettableBooleanMethodProperty(prop, null, _booleanSetters.size()));
    }
    public OptimizedSettableBeanProperty<?> addStringSetter(SettableBeanProperty prop) {
        int ix = _stringSetters.size();
        OptimizedSettableBeanProperty<?> optProp;
        switch (ix) {
        case 0:
            optProp = new SettableString0MethodProperty(prop, null, ix);
            break;
        case 1:
            optProp = new SettableString1MethodProperty(prop, null, ix);
            break;
        case 2:
            optProp = new SettableString2MethodProperty(prop, null, ix);
            break;
        case 3:
            optProp = new SettableString3MethodProperty(prop, null, ix);
            break;
        default:
            optProp = new SettableStringMethodProperty(prop, null, ix-4);
        }
        return _add(_stringSetters, optProp);
    }
    public OptimizedSettableBeanProperty<?> addObjectSetter(SettableBeanProperty prop) {
        int ix = _objectSetters.size();
        OptimizedSettableBeanProperty<?> optProp;
        switch (ix) {
        case 0:
            optProp = new SettableObject0MethodProperty(prop, null, ix);
            break;
        case 1:
            optProp = new SettableObject1MethodProperty(prop, null, ix);
            break;
        default:
            optProp = new SettableObjectMethodProperty(prop, null, ix-2);
        }
        return _add(_objectSetters, optProp);
    }

    public OptimizedSettableBeanProperty<?> addIntField(SettableBeanProperty prop) {
        int ix = _intFields.size();
        OptimizedSettableBeanProperty<?> optProp;
        switch (ix) {
        case 0:
            optProp = new SettableInt0FieldProperty(prop, null, ix);
            break;
        case 1:
            optProp = new SettableInt1FieldProperty(prop, null, ix);
            break;
        default:
            optProp = new SettableIntFieldProperty(prop, null, ix-2);
        }
        return _add(_intFields, optProp);
    }
    public OptimizedSettableBeanProperty<?> addLongField(SettableBeanProperty prop) {
        return _add(_longFields, new SettableLongFieldProperty(prop, null, _longFields.size()));
    }
    public OptimizedSettableBeanProperty<?> addBooleanField(SettableBeanProperty prop) {
        return _add(_booleanFields, new SettableBooleanFieldProperty(prop, null, _booleanFields.size()));
    }
    public OptimizedSettableBeanProperty<?> addStringField(SettableBeanProperty prop) {
        int ix = _stringFields.size();
        OptimizedSettableBeanProperty<?> optProp;
        switch (ix) {
        case 0:
            optProp = new SettableString0FieldProperty(prop, null, ix);
            break;
        case 1:
            optProp = new SettableString1FieldProperty(prop, null, ix);
            break;
        default:
            optProp = new SettableStringFieldProperty(prop, null, ix-2);
        }
        return _add(_stringFields, optProp);
    }
    public OptimizedSettableBeanProperty<?> addObjectField(SettableBeanProperty prop) {
        return _add(_objectFields, new SettableObjectFieldProperty(prop, null, _objectFields.size()));
    }

    /*
    /**********************************************************
    /* Code generation; high level
    /**********************************************************
     */

    /**
     * Method for building generic mutator class for specified bean
     * type.
     */
    public BeanPropertyMutator buildMutator(MyClassLoader classLoader)
    {
        // if we weren't passed a class loader, we will base it on value type CL, try to use parent
        if (classLoader == null) {
            classLoader = new MyClassLoader(beanClass.getClassLoader(), true);
        }

        final ClassName baseName = ClassName.constructFor(beanClass, "$Access4JacksonDeserializer");
        Class<?> accessorClass = generateMutatorClass(classLoader, baseName);
        try {
            return (BeanPropertyMutator) accessorClass.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate accessor class '"+accessorClass.getName()+"': "+e.getMessage(), e);
        }
    }

    public Class<?> generateMutatorClass(MyClassLoader classLoader, ClassName baseName)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String superClass = internalClassName(BeanPropertyMutator.class.getName());

        final String tmpClassName = baseName.getSlashedTemplate();

        // muchos important: level at least 1.5 to get generics!!!
        cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER + ACC_FINAL, tmpClassName,
                null, superClass, null);
        cw.visitSource(baseName.getSourceFilename(), null);

        // add default (no-arg) constructor first
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, superClass, "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0); // don't care (real values: 1,1)
        mv.visitEnd();

        // and then add various accessors; first field accessors:
        if (!_intFields.isEmpty()) {
            _addFields(cw, _intFields, "intField", SPECIAL_FIELDS_INT,
                    Type.INT_TYPE, ILOAD);
        }
        if (!_longFields.isEmpty()) {
            _addFields(cw, _longFields, "longField", SPECIAL_FIELDS_LONG,
                    Type.LONG_TYPE, LLOAD);
        }
        if (!_booleanFields.isEmpty()) {
            // booleans are simply ints 0 and 1
            _addFields(cw, _booleanFields, "booleanField", SPECIAL_FIELDS_BOOLEAN,
                    Type.BOOLEAN_TYPE, ILOAD);
        }
        if (!_stringFields.isEmpty()) {
            _addFields(cw, _stringFields, "stringField", SPECIAL_FIELDS_STRING,
                    STRING_TYPE, ALOAD);
        }
        if (!_objectFields.isEmpty()) {
            _addFields(cw, _objectFields, "objectField", SPECIAL_FIELDS_OBJECT,
                    OBJECT_TYPE, ALOAD);
        }

        // and then method accessors:
        if (!_intSetters.isEmpty()) {
            _addSetters(cw, _intSetters, "intSetter", SPECIAL_SETTERS_INT,
                    Type.INT_TYPE, ILOAD);
        }
        if (!_longSetters.isEmpty()) {
            _addSetters(cw, _longSetters, "longSetter", SPECIAL_SETTERS_LONG,
                    Type.LONG_TYPE, LLOAD);
        }
        if (!_booleanSetters.isEmpty()) {
            // booleans are simply ints 0 and 1
            _addSetters(cw, _booleanSetters, "booleanSetter", SPECIAL_SETTERS_BOOLEAN,
                    Type.BOOLEAN_TYPE, ILOAD);
        }
        if (!_stringSetters.isEmpty()) {
            _addSetters(cw, _stringSetters, "stringSetter", SPECIAL_SETTERS_STRING,
                    STRING_TYPE, ALOAD);
        }
        if (!_objectSetters.isEmpty()) {
            _addSetters(cw, _objectSetters, "objectSetter", SPECIAL_SETTERS_OBJECT,
                    OBJECT_TYPE, ALOAD);
        }

        cw.visitEnd();
        byte[] bytecode = cw.toByteArray();
        baseName.assignChecksum(bytecode);
        // already defined exactly as-is?
        try {
            return classLoader.loadClass(baseName.getDottedName());
        } catch (ClassNotFoundException e) { }
        // if not, load, resolve etc:
        return classLoader.loadAndResolve(baseName, bytecode);
    }

    /*
    /**********************************************************
    /* Code generation; method-based getters
    /**********************************************************
     */

    private void _addSetters(ClassWriter cw, List<OptimizedSettableBeanProperty<?>> props,
            String methodName, int specialCount, Type parameterType, int loadValueCode)
    {
        final boolean mustCast = parameterType.equals(OBJECT_TYPE);
        final boolean isLong = parameterType.equals(Type.LONG_TYPE);

        // First: create "special" optimized variants, if any:
        if (specialCount > 0) {
            int propCount = props.size();
            if (specialCount > propCount) {
                specialCount = propCount;
            }
            
            Iterator<OptimizedSettableBeanProperty<?>> it = props.iterator();
            for (int ix = 0 ; ix < specialCount; ++ix) {
                OptimizedSettableBeanProperty<?> prop = it.next();
                String specMethodName = methodName + ix;
                
                MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, specMethodName,
                        "(Ljava/lang/Object;"+parameterType+")V", /*generic sig*/null, null);
                mv.visitCode();
                // first: cast bean to proper type
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, beanClassName);
                // 3 args (0 == this), so 3 is the first local var slot, 4 for long
                // (one less than later on)
                int localVarIndex = 3 + (isLong ? 1 : 0);
                mv.visitVarInsn(ASTORE, localVarIndex);

                _addOptimizedSetter(mv, prop, loadValueCode, localVarIndex, mustCast);         
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
            // was that all?
            if (propCount <= specialCount) {
                return;
            }
            props = props.subList(specialCount, propCount);
        }

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, methodName, "(Ljava/lang/Object;I"+parameterType+")V", /*generic sig*/null, null);
        mv.visitCode();
        // first: cast bean to proper type
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, beanClassName);
        int localVarIndex = 4 + (isLong ? 1 : 0);
        mv.visitVarInsn(ASTORE, localVarIndex); // 3 args (0 == this), so 4 is the first local var slot, 5 for long

        // Ok; minor optimization, 3 or fewer accessors, just do IFs; over that, use switch
        switch (props.size()) {
        case 1:
            _addSingleSetter(mv, props.get(0), loadValueCode, localVarIndex, mustCast);
            break;
        case 2:
        case 3:
            _addSettersUsingIf(mv, props, loadValueCode, localVarIndex, mustCast);
            break;
        default:
            _addSettersUsingSwitch(mv, props, loadValueCode, localVarIndex, mustCast);
        }
        // and if no match, generate exception:
        generateException(mv, beanClassName, props.size());
        mv.visitMaxs(0, 0); // don't care (real values: 1,1)
        mv.visitEnd();
    }

    /*
    /**********************************************************
    /* Code generation; field-based getters
    /**********************************************************
     */

    private void _addFields(ClassWriter cw, List<OptimizedSettableBeanProperty<?>> props,
            String methodName, int specialCount, Type parameterType, int loadValueCode)
    {
        final boolean mustCast = parameterType.equals(OBJECT_TYPE);
        final boolean isLong = parameterType.equals(Type.LONG_TYPE);

        // First: create "special" optimized variants, if any:
        if (specialCount > 0) {
            int propCount = props.size();
            if (specialCount > propCount) {
                specialCount = propCount;
            }
            
            Iterator<OptimizedSettableBeanProperty<?>> it = props.iterator();
            for (int ix = 0 ; ix < specialCount; ++ix) {
                OptimizedSettableBeanProperty<?> prop = it.next();
                String specMethodName = methodName + ix;
                
                MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, specMethodName,
                        "(Ljava/lang/Object;"+parameterType+")V", /*generic sig*/null, null);
                mv.visitCode();
                // first: cast bean to proper type
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, beanClassName);
                // 3 args (0 == this), so 3 is the first local var slot, 4 for long
                // (one less than later on)
                int localVarIndex = 3 + (isLong ? 1 : 0);
                mv.visitVarInsn(ASTORE, localVarIndex);

                _addOptimizedField(mv, prop, loadValueCode, localVarIndex, mustCast);         
                mv.visitMaxs(0, 0); // don't care (real values: 1,1)
                mv.visitEnd();
            }
            // was that all?
            if (propCount <= specialCount) {
                return;
            }
            props = props.subList(specialCount, propCount);
        }
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, methodName, "(Ljava/lang/Object;I"+parameterType+")V", /*generic sig*/null, null);
        mv.visitCode();
        // first: cast bean to proper type
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, beanClassName);
        int localVarIndex = 4 + (isLong ? 1 : 0);
        mv.visitVarInsn(ASTORE, localVarIndex); // 3 args (0 == this), so 4 is the first local var slot, 5 for long

        // Ok; minor optimization, 3 or fewer fields, just do IFs; over that, use switch
        switch (props.size()) {
        case 1:
            _addSingleField(mv, props.get(0), loadValueCode, localVarIndex, mustCast);
            break;
        case 2:
        case 3:
            _addFieldsUsingIf(mv, props, loadValueCode, localVarIndex, mustCast);
            break;
        default:
            _addFieldsUsingSwitch(mv, props, loadValueCode, localVarIndex, mustCast);
        }
        // and if no match, generate exception:
        generateException(mv, beanClassName, props.size());
        mv.visitMaxs(0, 0); // don't care (real values: 1,1)
        mv.visitEnd();
    }

    /*
    /**********************************************************
    /* Helper methods, method accessor creation
    /**********************************************************
     */

    private void _addSingleSetter(MethodVisitor mv, OptimizedSettableBeanProperty<?> prop,
            int loadValueCode, int localVarOffset, boolean mustCast)
    {
        mv.visitVarInsn(ILOAD, 2); // load second arg (index)
        mv.visitVarInsn(ALOAD, localVarOffset); // load local for cast bean
        mv.visitVarInsn(loadValueCode, 3);
        Method method = (Method) (prop.getMember().getMember());
        Type type = Type.getType(method.getParameterTypes()[0]);
        if (mustCast) {
            mv.visitTypeInsn(CHECKCAST, type.getInternalName());
        }
        // Don't assume return type is 'void', we need to:
        Type returnType = Type.getType(method.getReturnType());

        boolean isInterface = method.getDeclaringClass().isInterface();
        mv.visitMethodInsn(isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL,
                beanClassName, method.getName(), "("+type+")"+returnType, isInterface);
        mv.visitInsn(RETURN);
    }

    private void _addOptimizedSetter(MethodVisitor mv, OptimizedSettableBeanProperty<?> prop,
            int loadValueCode, int localVarOffset, boolean mustCast)
    {
        mv.visitVarInsn(ALOAD, localVarOffset); // load local for pre-cast bean
        mv.visitVarInsn(loadValueCode, 2);
        Method method = (Method) (prop.getMember().getMember());
        Type type = Type.getType(method.getParameterTypes()[0]);
        if (mustCast) {
            mv.visitTypeInsn(CHECKCAST, type.getInternalName());
        }
        // Don't assume return type is 'void', we need to:
        Type returnType = Type.getType(method.getReturnType());

        boolean isInterface = method.getDeclaringClass().isInterface();
        mv.visitMethodInsn(isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL,
                beanClassName, method.getName(), "("+type+")"+returnType, isInterface);
        mv.visitInsn(RETURN);
    }

    private void _addSettersUsingIf(MethodVisitor mv,
            List<OptimizedSettableBeanProperty<?>> props, int loadValueCode, int beanIndex, boolean mustCast)
    {
        mv.visitVarInsn(ILOAD, 2); // load second arg (index)
        Label next = new Label();
        // first: check if 'index == 0'
        mv.visitJumpInsn(IFNE, next); // "if not zero, goto L (skip stuff)"
        // call first getter:
        mv.visitVarInsn(ALOAD, beanIndex); // load local for cast bean
        mv.visitVarInsn(loadValueCode, 3);
        Method method = (Method) (props.get(0).getMember().getMember());
        Type type = Type.getType(method.getParameterTypes()[0]);
        if (mustCast) {
            mv.visitTypeInsn(CHECKCAST, type.getInternalName());
        }
        // to fix [Issue-5] (don't assume return type is 'void'), we need to:
        Type returnType = Type.getType(method.getReturnType());

        boolean isInterface = method.getDeclaringClass().isInterface();
        mv.visitMethodInsn(isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL,
                beanClassName, method.getName(), "("+type+")"+returnType, isInterface);
        mv.visitInsn(RETURN);

        // And from this point on, loop a bit
        for (int i = 1, end = props.size()-1; i <= end; ++i) {
            mv.visitLabel(next);
            // No comparison needed for the last entry; assumed to match
            if (i < end) {
                next = new Label();
                mv.visitVarInsn(ILOAD, 2); // load second arg (index)
                mv.visitInsn(ALL_INT_CONSTS[i]);
                mv.visitJumpInsn(IF_ICMPNE, next);
            }
            mv.visitVarInsn(ALOAD, beanIndex); // load bean
            mv.visitVarInsn(loadValueCode, 3);
            method = (Method) (props.get(i).getMember().getMember());
            type = Type.getType(method.getParameterTypes()[0]);

            returnType = Type.getType(method.getReturnType());

            if (mustCast) {
                mv.visitTypeInsn(CHECKCAST, type.getInternalName());
            }
            isInterface = method.getDeclaringClass().isInterface();
            mv.visitMethodInsn(isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL,
                    beanClassName, method.getName(), "("+type+")"+returnType, isInterface);
            mv.visitInsn(RETURN);
        }
    }

    private void _addSettersUsingSwitch(MethodVisitor mv,
            List<OptimizedSettableBeanProperty<?>> props, int loadValueCode, int beanIndex, boolean mustCast)
    {
        mv.visitVarInsn(ILOAD, 2); // load second arg (index)

        Label[] labels = new Label[props.size()];
        for (int i = 0, len = labels.length; i < len; ++i) {
            labels[i] = new Label();
        }
        Label defaultLabel = new Label();
        mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);
        for (int i = 0, len = labels.length; i < len; ++i) {
            mv.visitLabel(labels[i]);
            mv.visitVarInsn(ALOAD, beanIndex); // load bean
            mv.visitVarInsn(loadValueCode, 3);
            Method method = (Method) (props.get(i).getMember().getMember());
            Type type = Type.getType(method.getParameterTypes()[0]);

            Type returnType = Type.getType(method.getReturnType());

            if (mustCast) {
                mv.visitTypeInsn(CHECKCAST, type.getInternalName());
            }
            boolean isInterface = method.getDeclaringClass().isInterface();
            mv.visitMethodInsn(isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL,
                    beanClassName, method.getName(), "("+type+")"+returnType, isInterface);
            mv.visitInsn(RETURN);
        }
        mv.visitLabel(defaultLabel);
    }

    /*
    /**********************************************************
    /* Helper methods, field accessor creation
    /**********************************************************
     */

    private void _addSingleField(MethodVisitor mv,
            OptimizedSettableBeanProperty<?> prop, int loadValueCode,
            int localVarOffset, boolean mustCast)
    {
        mv.visitVarInsn(ILOAD, 2); // load second arg (index)
        mv.visitVarInsn(ALOAD, localVarOffset); // load local for cast bean
        mv.visitVarInsn(loadValueCode, 3);
        AnnotatedField field = (AnnotatedField) prop.getMember();
        Type type = Type.getType(field.getRawType());
        if (mustCast) {
            mv.visitTypeInsn(CHECKCAST, type.getInternalName());
        }
        mv.visitFieldInsn(PUTFIELD, beanClassName, field.getName(), type.getDescriptor());
        mv.visitInsn(RETURN);
    }

    // Same as above, except no index needed or passed
    private void _addOptimizedField(MethodVisitor mv,
            OptimizedSettableBeanProperty<?> prop, int loadValueCode,
            int localVarOffset, boolean mustCast)
    {
        mv.visitVarInsn(ALOAD, localVarOffset); // load local for pre-cast bean
        mv.visitVarInsn(loadValueCode, 2);
        AnnotatedField field = (AnnotatedField) prop.getMember();
        Type type = Type.getType(field.getRawType());
        if (mustCast) {
            mv.visitTypeInsn(CHECKCAST, type.getInternalName());
        }
        mv.visitFieldInsn(PUTFIELD, beanClassName, field.getName(), type.getDescriptor());
        mv.visitInsn(RETURN);
    }
    
    private void _addFieldsUsingIf(MethodVisitor mv,
            List<OptimizedSettableBeanProperty<?>> props, int loadValueCode, int beanIndex, boolean mustCast)
    {
        mv.visitVarInsn(ILOAD, 2); // load second arg (index)
        Label next = new Label();
        // first: check if 'index == 0'
        mv.visitJumpInsn(IFNE, next); // "if not zero, goto L (skip stuff)"

        // first field accessor
        mv.visitVarInsn(ALOAD, beanIndex); // load local for cast bean
        mv.visitVarInsn(loadValueCode, 3);
        AnnotatedField field = (AnnotatedField) props.get(0).getMember();
        Type type = Type.getType(field.getRawType());
        if (mustCast) {
            mv.visitTypeInsn(CHECKCAST, type.getInternalName());
        }
        mv.visitFieldInsn(PUTFIELD, beanClassName, field.getName(), type.getDescriptor());
        mv.visitInsn(RETURN);

        // And from this point on, loop a bit
        for (int i = 1, end = props.size()-1; i <= end; ++i) {
            mv.visitLabel(next);
            // No comparison needed for the last entry; assumed to match
            if (i < end) {
                next = new Label();
                mv.visitVarInsn(ILOAD, 2); // load second arg (index)
                mv.visitInsn(ALL_INT_CONSTS[i]);
                mv.visitJumpInsn(IF_ICMPNE, next);
            }
            mv.visitVarInsn(ALOAD, beanIndex); // load bean
            mv.visitVarInsn(loadValueCode, 3);
            field = (AnnotatedField) props.get(i).getMember();
            type = Type.getType(field.getRawType());
            if (mustCast) {
                mv.visitTypeInsn(CHECKCAST, type.getInternalName());
            }
            mv.visitFieldInsn(PUTFIELD, beanClassName, field.getName(), type.getDescriptor());
            mv.visitInsn(RETURN);
        }
    }

    private void _addFieldsUsingSwitch(MethodVisitor mv, List<OptimizedSettableBeanProperty<?>> props,
            int loadValueCode, int beanIndex, boolean mustCast)
    {
        mv.visitVarInsn(ILOAD, 2); // load second arg (index)

        Label[] labels = new Label[props.size()];
        for (int i = 0, len = labels.length; i < len; ++i) {
            labels[i] = new Label();
        }
        Label defaultLabel = new Label();
        mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);
        for (int i = 0, len = labels.length; i < len; ++i) {
            mv.visitLabel(labels[i]);
            mv.visitVarInsn(ALOAD, beanIndex); // load bean
            mv.visitVarInsn(loadValueCode, 3); // put 'value' to stack
            AnnotatedField field = (AnnotatedField) props.get(i).getMember();
            Type type = Type.getType(field.getRawType());
            if (mustCast) {
                mv.visitTypeInsn(CHECKCAST, type.getInternalName());
            }
            mv.visitFieldInsn(PUTFIELD, beanClassName, field.getName(), type.getDescriptor());
            mv.visitInsn(RETURN);
        }
        mv.visitLabel(defaultLabel);
    }
}
