package com.fasterxml.jackson.module.afterburner.ser;

import java.lang.reflect.Method;
import java.util.*;

import org.objectweb.asm.*;

import static org.objectweb.asm.Opcodes.*;

import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.module.afterburner.util.ClassName;
import com.fasterxml.jackson.module.afterburner.util.DynamicPropertyAccessorBase;
import com.fasterxml.jackson.module.afterburner.util.MyClassLoader;

/**
 * Simple collector used to keep track of properties for which code-generated
 * accessors are needed.
 */
public class PropertyAccessorCollector
    extends DynamicPropertyAccessorBase
{
    private static final Type STRING_TYPE = Type.getType(String.class);
    private static final Type OBJECT_TYPE = Type.getType(Object.class);

    private final List<BooleanMethodPropertyWriter> _booleanGetters = new LinkedList<BooleanMethodPropertyWriter>();
    private final List<IntMethodPropertyWriter> _intGetters = new LinkedList<IntMethodPropertyWriter>();
    private final List<LongMethodPropertyWriter> _longGetters = new LinkedList<LongMethodPropertyWriter>();
    private final List<StringMethodPropertyWriter> _stringGetters = new LinkedList<StringMethodPropertyWriter>();
    private final List<ObjectMethodPropertyWriter> _objectGetters = new LinkedList<ObjectMethodPropertyWriter>();
    
    private final List<BooleanFieldPropertyWriter> _booleanFields = new LinkedList<BooleanFieldPropertyWriter>();
    private final List<IntFieldPropertyWriter> _intFields = new LinkedList<IntFieldPropertyWriter>();
    private final List<LongFieldPropertyWriter> _longFields = new LinkedList<LongFieldPropertyWriter>();
    private final List<StringFieldPropertyWriter> _stringFields = new LinkedList<StringFieldPropertyWriter>();
    private final List<ObjectFieldPropertyWriter> _objectFields = new LinkedList<ObjectFieldPropertyWriter>();

    private final Class<?> beanClass;
    private final String beanClassName;

    public PropertyAccessorCollector(Class<?> beanClass) {
        this.beanClass = beanClass;
        this.beanClassName = Type.getInternalName(beanClass);
    }
    
    /*
    /**********************************************************
    /* Methods for collecting properties
    /**********************************************************
     */

    public BooleanMethodPropertyWriter addBooleanGetter(BeanPropertyWriter bpw) {
        return _add(_booleanGetters, new BooleanMethodPropertyWriter(bpw, null, _booleanGetters.size(), null));
    }
    public IntMethodPropertyWriter addIntGetter(BeanPropertyWriter bpw) {
        return _add(_intGetters, new IntMethodPropertyWriter(bpw, null, _intGetters.size(), null));
    }
    public LongMethodPropertyWriter addLongGetter(BeanPropertyWriter bpw) {
        return _add(_longGetters, new LongMethodPropertyWriter(bpw, null, _longGetters.size(), null));
    }
    public StringMethodPropertyWriter addStringGetter(BeanPropertyWriter bpw) {
        return _add(_stringGetters, new StringMethodPropertyWriter(bpw, null, _stringGetters.size(), null));
    }
    public ObjectMethodPropertyWriter addObjectGetter(BeanPropertyWriter bpw) {
        return _add(_objectGetters, new ObjectMethodPropertyWriter(bpw, null, _objectGetters.size(), null));
    }

    public BooleanFieldPropertyWriter addBooleanField(BeanPropertyWriter bpw) {
        return _add(_booleanFields, new BooleanFieldPropertyWriter(bpw, null, _booleanFields.size(), null));
    }
    public IntFieldPropertyWriter addIntField(BeanPropertyWriter bpw) {
        return _add(_intFields, new IntFieldPropertyWriter(bpw, null, _intFields.size(), null));
    }
    public LongFieldPropertyWriter addLongField(BeanPropertyWriter bpw) {
        return _add(_longFields, new LongFieldPropertyWriter(bpw, null, _longFields.size(), null));
    }
    public StringFieldPropertyWriter addStringField(BeanPropertyWriter bpw) {
        return _add(_stringFields, new StringFieldPropertyWriter(bpw, null, _stringFields.size(), null));
    }
    public ObjectFieldPropertyWriter addObjectField(BeanPropertyWriter bpw) {
        return _add(_objectFields, new ObjectFieldPropertyWriter(bpw, null, _objectFields.size(), null));
    }

    /*
    /**********************************************************
    /* Code generation; high level
    /**********************************************************
     */

    public BeanPropertyAccessor findAccessor(MyClassLoader classLoader)
    {
        // if we weren't passed a class loader, we will base it on value type CL, try to use parent
        if (classLoader == null) {
            classLoader = new MyClassLoader(beanClass.getClassLoader(), true);
        }
        final ClassName baseName = ClassName.constructFor(beanClass, "$Access4JacksonDeserializer");
        Class<?> accessorClass = generateAccessorClass(classLoader, baseName);
        try {
            return (BeanPropertyAccessor) accessorClass.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate accessor class '"+accessorClass.getName()+"': "+e.getMessage(), e);
        }
    }

    public Class<?> generateAccessorClass(MyClassLoader classLoader, ClassName baseName)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String superClass = internalClassName(BeanPropertyAccessor.class.getName());
        final String tmpClassName = baseName.getSlashedTemplate();
        
        // muchos important: level at least 1.5 to get generics!!!
        // also: since we require JDK 1.6 anyway, use that starting with Jackson 2.5

        cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER + ACC_FINAL, tmpClassName,
                null, superClass, null);
        cw.visitSource(baseName.getSourceFilename(), null);

        // add default (no-arg) constructor:
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, superClass, "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0); // don't care (real values: 1,1)
        mv.visitEnd();

        // and then add various accessors; first field accessors:
        if (!_intFields.isEmpty()) {
            _addFields(cw, _intFields, "intField", Type.INT_TYPE, IRETURN);
        }
        if (!_longFields.isEmpty()) {
            _addFields(cw, _longFields, "longField", Type.LONG_TYPE, LRETURN);
        }
        if (!_stringFields.isEmpty()) {
            _addFields(cw, _stringFields, "stringField", STRING_TYPE, ARETURN);
        }
        if (!_objectFields.isEmpty()) {
            _addFields(cw, _objectFields, "objectField", OBJECT_TYPE, ARETURN);
        }
        if (!_booleanFields.isEmpty()) {
            // booleans treated as ints 0 (false) and 1 (true)
            _addFields(cw, _booleanFields, "booleanField", Type.BOOLEAN_TYPE, IRETURN);
        }

        // and then method accessors:
        if (!_intGetters.isEmpty()) {
            _addGetters(cw, _intGetters, "intGetter", Type.INT_TYPE, IRETURN);
        }
        if (!_longGetters.isEmpty()) {
            _addGetters(cw, _longGetters, "longGetter", Type.LONG_TYPE, LRETURN);
        }
        if (!_stringGetters.isEmpty()) {
            _addGetters(cw, _stringGetters, "stringGetter", STRING_TYPE, ARETURN);
        }
        if (!_objectGetters.isEmpty()) {
            _addGetters(cw, _objectGetters, "objectGetter", OBJECT_TYPE, ARETURN);
        }
        if (!_booleanGetters.isEmpty()) {
            _addGetters(cw, _booleanGetters, "booleanGetter", Type.BOOLEAN_TYPE, IRETURN);
        }

        cw.visitEnd();
        byte[] bytecode = cw.toByteArray();
        baseName.assignChecksum(bytecode);

        // Did we already generate this?
        try {
            return classLoader.loadClass(baseName.getDottedName());
        } catch (ClassNotFoundException e) { }
        // if not, load and resolve:
        return classLoader.loadAndResolve(baseName, bytecode);
    }

    /*
    /**********************************************************
    /* Code generation; method-based getters
    /**********************************************************
     */

    private <T extends OptimizedBeanPropertyWriter<T>> void _addGetters(ClassWriter cw, List<T> props,
            String methodName, Type returnType, int returnOpcode)
    {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, methodName, "(Ljava/lang/Object;I)"+returnType, /*generic sig*/null, null);
        mv.visitCode();
        // first: cast bean to proper type
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, beanClassName);
        mv.visitVarInsn(ASTORE, 3);

        // Ok; minor optimization, 3 or fewer accessors, just do IFs; over that, use switch
        switch (props.size()) {
        case 1:
            _addSingleGetter(mv, props.get(0), returnOpcode);
            break;
        case 2:
        case 3:
            _addGettersUsingIf(mv, props, returnOpcode);
            break;
        default:
            _addGettersUsingSwitch(mv, props, returnOpcode);
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
    
    private <T extends OptimizedBeanPropertyWriter<T>> void _addFields(ClassWriter cw, List<T> props,
            String methodName, Type returnType, int returnOpcode)
    {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, methodName, "(Ljava/lang/Object;I)"+returnType, /*generic sig*/null, null);
        mv.visitCode();
        // first: cast bean to proper type
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, beanClassName);
        mv.visitVarInsn(ASTORE, 3);

        // Ok; minor optimization, 3 or fewer fields, just do IFs; over that, use switch
        switch (props.size()) {
        case 1:
            _addSingleField(mv, props.get(0), returnOpcode);
            break;
        case 2:
        case 3:
            _addFieldsUsingIf(mv, props, returnOpcode);
            break;
        default:
            _addFieldsUsingSwitch(mv, props, returnOpcode);
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

    private void _addSingleGetter(MethodVisitor mv,
            OptimizedBeanPropertyWriter<?> prop, int returnOpcode)
    {
        mv.visitVarInsn(ILOAD, 2); // load second arg (index)
        mv.visitVarInsn(ALOAD, 3); // load local for cast bean
        int invokeInsn = beanClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL;
        Method method = (Method) (prop.getMember().getMember());
        mv.visitMethodInsn(invokeInsn, beanClassName, method.getName(),
                Type.getMethodDescriptor(method), beanClass.isInterface());
        mv.visitInsn(returnOpcode);
    }
    
    private <T extends OptimizedBeanPropertyWriter<T>> void _addGettersUsingIf(MethodVisitor mv,
            List<T> props, int returnOpcode)
    {
        mv.visitVarInsn(ILOAD, 2); // load second arg (index)
        Label next = new Label();
        // first: check if 'index == 0'
        mv.visitJumpInsn(IFNE, next); // "if not zero, goto L (skip stuff)"

        // call first getter:
        mv.visitVarInsn(ALOAD, 3); // load local for cast bean
        int invokeInsn = beanClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL;
        Method method = (Method) (props.get(0).getMember().getMember());
        mv.visitMethodInsn(invokeInsn, beanClassName, method.getName(),
                Type.getMethodDescriptor(method), beanClass.isInterface());
        mv.visitInsn(returnOpcode);

        // And from this point on, loop a bit
        for (int i = 1, end = props.size()-1; i <= end; ++i) {
            mv.visitLabel(next);
            // No need to check index for the last one
            if (i < end) {
                next = new Label();
                mv.visitVarInsn(ILOAD, 2); // load second arg (index)
                mv.visitInsn(ALL_INT_CONSTS[i]);
                mv.visitJumpInsn(IF_ICMPNE, next);
            }
            mv.visitVarInsn(ALOAD, 3); // load bean
            method = (Method) (props.get(i).getMember().getMember());
            mv.visitMethodInsn(invokeInsn, beanClassName, method.getName(),
                    Type.getMethodDescriptor(method), beanClass.isInterface());
            mv.visitInsn(returnOpcode);
        }
    }

    private <T extends OptimizedBeanPropertyWriter<T>> void _addGettersUsingSwitch(MethodVisitor mv,
            List<T> props, int returnOpcode)
    {
        mv.visitVarInsn(ILOAD, 2); // load second arg (index)

        Label[] labels = new Label[props.size()];
        for (int i = 0, len = labels.length; i < len; ++i) {
            labels[i] = new Label();
        }
        Label defaultLabel = new Label();
        mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);
        int invokeInsn = beanClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL;
        for (int i = 0, len = labels.length; i < len; ++i) {
            mv.visitLabel(labels[i]);
            mv.visitVarInsn(ALOAD, 3); // load bean
            Method method = (Method) (props.get(i).getMember().getMember());
            mv.visitMethodInsn(invokeInsn, beanClassName, method.getName(),
                    Type.getMethodDescriptor(method), beanClass.isInterface());
            mv.visitInsn(returnOpcode);
        }
        mv.visitLabel(defaultLabel);
    }

    private void _addSingleField(MethodVisitor mv, OptimizedBeanPropertyWriter<?> prop, int returnOpcode)
    {
        mv.visitVarInsn(ILOAD, 2); // load second arg (index)
        mv.visitVarInsn(ALOAD, 3); // load local for cast bean
        AnnotatedField field = (AnnotatedField) prop.getMember();
        mv.visitFieldInsn(GETFIELD, beanClassName, field.getName(), Type.getDescriptor(field.getRawType()));
        mv.visitInsn(returnOpcode);
    }
    
    private <T extends OptimizedBeanPropertyWriter<T>> void _addFieldsUsingIf(MethodVisitor mv,
            List<T> props, int returnOpcode)
    {
        mv.visitVarInsn(ILOAD, 2); // load second arg (index)
        Label next = new Label();
        // first: check if 'index == 0'
        mv.visitJumpInsn(IFNE, next); // "if not zero, goto L (skip stuff)"

        // first field accessor
        mv.visitVarInsn(ALOAD, 3); // load local for cast bean
        AnnotatedField field = (AnnotatedField) props.get(0).getMember();
        mv.visitFieldInsn(GETFIELD, beanClassName, field.getName(), Type.getDescriptor(field.getRawType()));
        mv.visitInsn(returnOpcode);

        // And from this point on, loop a bit
        for (int i = 1, end = props.size()-1; i <= end; ++i) {
            mv.visitLabel(next);
            // No need to check index for the last one
            if (i < end) {
                next = new Label();
                mv.visitVarInsn(ILOAD, 2); // load second arg (index)
                mv.visitInsn(ALL_INT_CONSTS[i]);
                mv.visitJumpInsn(IF_ICMPNE, next);
            }
            mv.visitVarInsn(ALOAD, 3); // load bean
            field = (AnnotatedField) props.get(i).getMember();
            mv.visitFieldInsn(GETFIELD, beanClassName, field.getName(), Type.getDescriptor(field.getRawType()));
            mv.visitInsn(returnOpcode);
        }
    }

    private <T extends OptimizedBeanPropertyWriter<T>> void _addFieldsUsingSwitch(MethodVisitor mv,
            List<T> props, int returnOpcode)
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
            mv.visitVarInsn(ALOAD, 3); // load bean
            AnnotatedField field = (AnnotatedField) props.get(i).getMember();
            mv.visitFieldInsn(GETFIELD, beanClassName, field.getName(), Type.getDescriptor(field.getRawType()));
            mv.visitInsn(returnOpcode);
        }
        mv.visitLabel(defaultLabel);
    }
}
