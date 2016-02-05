package com.fasterxml.jackson.module.afterburner.deser;

import java.lang.reflect.Method;
import java.util.*;

import org.objectweb.asm.*;

import static org.objectweb.asm.Opcodes.*;

import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
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

    private final List<SettableIntMethodProperty> _intSetters = new LinkedList<SettableIntMethodProperty>();
    private final List<SettableLongMethodProperty> _longSetters = new LinkedList<SettableLongMethodProperty>();
    private final List<SettableBooleanMethodProperty> _booleanSetters = new LinkedList<SettableBooleanMethodProperty>();
    private final List<SettableStringMethodProperty> _stringSetters = new LinkedList<SettableStringMethodProperty>();
    private final List<SettableObjectMethodProperty> _objectSetters = new LinkedList<SettableObjectMethodProperty>();

    private final List<SettableIntFieldProperty> _intFields = new LinkedList<SettableIntFieldProperty>();
    private final List<SettableLongFieldProperty> _longFields = new LinkedList<SettableLongFieldProperty>();
    private final List<SettableBooleanFieldProperty> _booleanFields = new LinkedList<SettableBooleanFieldProperty>();
    private final List<SettableStringFieldProperty> _stringFields = new LinkedList<SettableStringFieldProperty>();
    private final List<SettableObjectFieldProperty> _objectFields = new LinkedList<SettableObjectFieldProperty>();

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

    public SettableIntMethodProperty addIntSetter(SettableBeanProperty prop) {
        return _add(_intSetters, new SettableIntMethodProperty(prop, null, _intSetters.size()));
    }
    public SettableLongMethodProperty addLongSetter(SettableBeanProperty prop) {
        return _add(_longSetters, new SettableLongMethodProperty(prop, null, _longSetters.size()));
    }
    public SettableBooleanMethodProperty addBooleanSetter(SettableBeanProperty prop) {
        return _add(_booleanSetters, new SettableBooleanMethodProperty(prop, null, _booleanSetters.size()));
    }
    public SettableStringMethodProperty addStringSetter(SettableBeanProperty prop) {
        return _add(_stringSetters, new SettableStringMethodProperty(prop, null, _stringSetters.size()));
    }
    public SettableObjectMethodProperty addObjectSetter(SettableBeanProperty prop) {
        return _add(_objectSetters, new SettableObjectMethodProperty(prop, null, _objectSetters.size()));
    }

    public SettableIntFieldProperty addIntField(SettableBeanProperty prop) {
        return _add(_intFields, new SettableIntFieldProperty(prop, null, _intFields.size()));
    }
    public SettableLongFieldProperty addLongField(SettableBeanProperty prop) {
        return _add(_longFields, new SettableLongFieldProperty(prop, null, _longFields.size()));
    }
    public SettableBooleanFieldProperty addBooleanField(SettableBeanProperty prop) {
        return _add(_booleanFields, new SettableBooleanFieldProperty(prop, null, _booleanFields.size()));
    }
    public SettableStringFieldProperty addStringField(SettableBeanProperty prop) {
        return _add(_stringFields, new SettableStringFieldProperty(prop, null, _stringFields.size()));
    }
    public SettableObjectFieldProperty addObjectField(SettableBeanProperty prop) {
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

        // then two-argument constructor to be used by "with"
        String ctorSig = String.format("(L%s;I)V",
                internalClassName(SettableBeanProperty.class.getName()));
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", ctorSig, null, null);

        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitMethodInsn(INVOKESPECIAL, superClass, "<init>", ctorSig, false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0); // don't care (real values: 1,1)
        mv.visitEnd();

        // same signature as 2-arg constructor:
        String withSig = String.format("(L%s;I)L%s;",
                internalClassName(SettableBeanProperty.class.getName()), superClass);
        mv = cw.visitMethod(ACC_PUBLIC, "with", withSig, null, null);
        mv.visitCode();
        mv.visitTypeInsn(NEW, tmpClassName);
        mv.visitInsn(DUP);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitMethodInsn(INVOKESPECIAL, tmpClassName, "<init>", ctorSig, false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0); // don't care (real values: 1,1)
        mv.visitEnd();
        
        
        // and then add various accessors; first field accessors:
        if (!_intFields.isEmpty()) {
            _addFields(cw, _intFields, "intField", Type.INT_TYPE, ILOAD);
        }
        if (!_longFields.isEmpty()) {
            _addFields(cw, _longFields, "longField", Type.LONG_TYPE, LLOAD);
        }
        if (!_booleanFields.isEmpty()) {
            // booleans are simply ints 0 and 1
            _addFields(cw, _booleanFields, "booleanField", Type.BOOLEAN_TYPE, ILOAD);
        }
        if (!_stringFields.isEmpty()) {
            _addFields(cw, _stringFields, "stringField", STRING_TYPE, ALOAD);
        }
        if (!_objectFields.isEmpty()) {
            _addFields(cw, _objectFields, "objectField", OBJECT_TYPE, ALOAD);
        }

        // and then method accessors:
        if (!_intSetters.isEmpty()) {
            _addSetters(cw, _intSetters, "intSetter", Type.INT_TYPE, ILOAD);
        }
        if (!_longSetters.isEmpty()) {
            _addSetters(cw, _longSetters, "longSetter", Type.LONG_TYPE, LLOAD);
        }
        if (!_booleanSetters.isEmpty()) {
            // booleans are simply ints 0 and 1
            _addSetters(cw, _booleanSetters, "booleanSetter", Type.BOOLEAN_TYPE, ILOAD);
        }
        if (!_stringSetters.isEmpty()) {
            _addSetters(cw, _stringSetters, "stringSetter", STRING_TYPE, ALOAD);
        }
        if (!_objectSetters.isEmpty()) {
            _addSetters(cw, _objectSetters, "objectSetter", OBJECT_TYPE, ALOAD);
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

    private <T extends OptimizedSettableBeanProperty<T>> void _addSetters(ClassWriter cw, List<T> props,
            String methodName, Type parameterType, int loadValueCode)
    {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, methodName, "(Ljava/lang/Object;I"+parameterType+")V", /*generic sig*/null, null);
        mv.visitCode();
        // first: cast bean to proper type
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, beanClassName);
        int localVarIndex = 4 + (parameterType.equals(Type.LONG_TYPE) ? 1 : 0);
        mv.visitVarInsn(ASTORE, localVarIndex); // 3 args (0 == this), so 4 is the first local var slot, 5 for long

        boolean mustCast = parameterType.equals(OBJECT_TYPE);
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

    private <T extends OptimizedSettableBeanProperty<T>> void _addFields(ClassWriter cw, List<T> props,
            String methodName, Type parameterType, int loadValueCode)
    {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, methodName, "(Ljava/lang/Object;I"+parameterType+")V", /*generic sig*/null, null);
        mv.visitCode();
        // first: cast bean to proper type
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, beanClassName);
        int localVarIndex = 4 + (parameterType.equals(Type.LONG_TYPE) ? 1 : 0);
        mv.visitVarInsn(ASTORE, localVarIndex); // 3 args (0 == this), so 4 is the first local var slot, 5 for long

        boolean mustCast = parameterType.equals(OBJECT_TYPE);
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

    private void _addSingleSetter(MethodVisitor mv,
            OptimizedSettableBeanProperty<?> prop, int loadValueCode, int beanIndex, boolean mustCast)
    {
        mv.visitVarInsn(ILOAD, 2); // load second arg (index)
        mv.visitVarInsn(ALOAD, beanIndex); // load local for cast bean
        mv.visitVarInsn(loadValueCode, 3);
        Method method = (Method) (prop.getMember().getMember());
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
    }
    
    private <T extends OptimizedSettableBeanProperty<T>> void _addSettersUsingIf(MethodVisitor mv,
            List<T> props, int loadValueCode, int beanIndex, boolean mustCast)
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

    private <T extends OptimizedSettableBeanProperty<T>> void _addSettersUsingSwitch(MethodVisitor mv,
            List<T> props, int loadValueCode, int beanIndex, boolean mustCast)
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
            OptimizedSettableBeanProperty<?> prop, int loadValueCode, int beanIndex, boolean mustCast)
    {
        mv.visitVarInsn(ILOAD, 2); // load second arg (index)
        mv.visitVarInsn(ALOAD, beanIndex); // load local for cast bean
        mv.visitVarInsn(loadValueCode, 3);
        AnnotatedField field = (AnnotatedField) prop.getMember();
        Type type = Type.getType(field.getRawType());
        if (mustCast) {
            mv.visitTypeInsn(CHECKCAST, type.getInternalName());
        }
        mv.visitFieldInsn(PUTFIELD, beanClassName, field.getName(), type.getDescriptor());
        mv.visitInsn(RETURN);
    }
    
    private <T extends OptimizedSettableBeanProperty<T>> void _addFieldsUsingIf(MethodVisitor mv,
            List<T> props, int loadValueCode, int beanIndex, boolean mustCast)
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

    private <T extends OptimizedSettableBeanProperty<T>> void _addFieldsUsingSwitch(MethodVisitor mv,
            List<T> props, int loadValueCode, int beanIndex, boolean mustCast)
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
