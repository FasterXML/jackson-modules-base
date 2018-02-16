package com.fasterxml.jackson.module.afterburner.ser;

import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.module.afterburner.deser.PropertyMutatorCollector;
import com.fasterxml.jackson.module.afterburner.util.ClassName;
import com.fasterxml.jackson.module.afterburner.util.DynamicPropertyAccessorBase;
import com.fasterxml.jackson.module.afterburner.util.MyClassLoader;
import com.fasterxml.jackson.module.afterburner.util.bytebuddy.*;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.modifier.TypeManifestation;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.member.FieldAccess;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.implementation.bytecode.member.MethodReturn;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import net.bytebuddy.jar.asm.MethodVisitor;

import java.util.*;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Simple collector used to keep track of properties for which code-generated
 * accessors are needed.
 */
public class PropertyAccessorCollector
    extends DynamicPropertyAccessorBase
{
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
    private final TypeDescription beanClassDefinition;

    public PropertyAccessorCollector(Class<?> beanClass) {
        this.beanClass = beanClass;
        this.beanClassDefinition = new TypeDescription.ForLoadedType(beanClass);
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
        final ClassName baseName = ClassName.constructFor(beanClass, "$Access4JacksonSerializer");
        Class<?> accessorClass = generateAccessorClass(classLoader, baseName);
        try {
            return (BeanPropertyAccessor) accessorClass.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate accessor class '"+accessorClass.getName()+"': "+e.getMessage(), e);
        }
    }

    public Class<?> generateAccessorClass(MyClassLoader classLoader, ClassName baseName)
    {
        DynamicType.Builder<?> builder =
                new ByteBuddy(ClassFileVersion.JAVA_V6)
                        .with(TypeValidation.DISABLED)
                        .subclass(BeanPropertyAccessor.class, ConstructorStrategy.Default.DEFAULT_CONSTRUCTOR)
                        .name(baseName.getSlashedTemplate())
                        .modifiers(Visibility.PUBLIC, TypeManifestation.FINAL);

        // and then add various accessors; first field accessors:
        if (!_intFields.isEmpty()) {
            builder = _addFields(builder, _intFields, "intField", MethodReturn.INTEGER);
        }
        if (!_longFields.isEmpty()) {
            builder = _addFields(builder, _longFields, "longField", MethodReturn.LONG);
        }
        if (!_stringFields.isEmpty()) {
            builder = _addFields(builder, _stringFields, "stringField", MethodReturn.REFERENCE);
        }
        if (!_objectFields.isEmpty()) {
            builder = _addFields(builder, _objectFields, "objectField", MethodReturn.REFERENCE);
        }
        if (!_booleanFields.isEmpty()) {
            // booleans treated as ints 0 (false) and 1 (true)
            builder = _addFields(builder, _booleanFields, "booleanField", MethodReturn.INTEGER);
        }

        // and then method accessors:
        if (!_intGetters.isEmpty()) {
            builder = _addGetters(builder, _intGetters, "intGetter", MethodReturn.INTEGER);
        }
        if (!_longGetters.isEmpty()) {
            builder = _addGetters(builder, _longGetters, "longGetter", MethodReturn.LONG);
        }
        if (!_stringGetters.isEmpty()) {
            builder = _addGetters(builder, _stringGetters, "stringGetter", MethodReturn.REFERENCE);
        }
        if (!_objectGetters.isEmpty()) {
            builder = _addGetters(builder, _objectGetters, "objectGetter", MethodReturn.REFERENCE);
        }
        if (!_booleanGetters.isEmpty()) {
            builder = _addGetters(builder, _booleanGetters, "booleanGetter", MethodReturn.INTEGER);
        }

        byte[] bytecode = builder.make().getBytes();
        baseName.assignChecksum(bytecode);

        // Did we already generate this?
        try {
            return classLoader.loadClass(baseName.getDottedName());
        } catch (ClassNotFoundException e) { }
        // if not, load and resolve:
        return classLoader.loadAndResolve(baseName, bytecode);
    }

    /*
    /****************************************************************************
    /* Code generation; Byte Buddy common between fields and setters handling
    /****************************************************************************
     */

    /**
     * Implementation specific to {@link PropertyMutatorCollector}
     * We now that there are three arguments to the methods created in this case
     */
    private enum LocalVarIndexCalculator implements AbstractPropertyStackManipulation.LocalVarIndexCalculator {

        INSTANCE;

        //we have 2 arguments (plus arg 0 which is 'this'), so 3 is always the position of the local var
        @Override
        public int calculate() {
            return 3;
        }
    }

    private static class CreateLocalVarStackManipulation extends AbstractCreateLocalVarStackManipulation {

        CreateLocalVarStackManipulation(TypeDescription beanClassDescription) {
            super(beanClassDescription, PropertyAccessorCollector.LocalVarIndexCalculator.INSTANCE);
        }

        private static Map<TypeDescription, CreateLocalVarStackManipulation> cache
                = new HashMap<TypeDescription, CreateLocalVarStackManipulation>();

        static CreateLocalVarStackManipulation of(TypeDescription beanClassDescription) {
            CreateLocalVarStackManipulation result = cache.get(beanClassDescription);
            if (result == null) {
                result = new CreateLocalVarStackManipulation(beanClassDescription);
                cache.put(beanClassDescription, result);
            }
            return result;
        }
    }

    /**
     * Provides template for a method that returns a single getter or field of the bean
     */
    private abstract static class AbstractSinglePropStackManipulation<T extends OptimizedBeanPropertyWriter<T>>
            extends AbstractPropertyStackManipulation {

        private final TypeDescription beanClassDescription;
        private final T prop;
        private final MethodReturn methodReturn;

        AbstractSinglePropStackManipulation(TypeDescription beanClassDescription,
                                        T prop,
                                        MethodReturn methodReturn) {
            super(PropertyAccessorCollector.LocalVarIndexCalculator.INSTANCE);
            this.methodReturn = methodReturn;
            this.beanClassDescription = beanClassDescription;
            this.prop = prop;
        }

        abstract protected StackManipulation invocationOperation(
                AnnotatedMember annotatedMember, TypeDefinition def);

        @Override
        public Size apply(MethodVisitor methodVisitor,
                          Implementation.Context implementationContext) {

            final List<StackManipulation> operations = new ArrayList<StackManipulation>();
            operations.add(loadLocalVar()); // load local for cast bean

            final AnnotatedMember member = prop.getMember();

            operations.add(invocationOperation(member, beanClassDescription));
            operations.add(methodReturn);

            final StackManipulation.Compound compound = new StackManipulation.Compound(operations);
            return compound.apply(methodVisitor, implementationContext);
        }

        private StackManipulation loadLocalVar() {
            return MethodVariableAccess.REFERENCE.loadFrom(localVarIndex());
        }
    }


    /*
    /**********************************************************
    /* Code generation; method-based getters
    /**********************************************************
     */

    private static class SingleMethodStackManipulation<T extends OptimizedBeanPropertyWriter<T>>
            extends AbstractSinglePropStackManipulation<T> {

        SingleMethodStackManipulation(TypeDescription beanClassDescription,
                                            T prop,
                                            MethodReturn methodReturn) {
            super(beanClassDescription, prop, methodReturn);
        }

        @Override
        protected StackManipulation invocationOperation(
                AnnotatedMember annotatedMember, TypeDefinition beanClassDescription) {

            final String methodName = annotatedMember.getName();
            @SuppressWarnings("unchecked")
            final MethodList<MethodDescription> matchingMethods =
                    (MethodList<MethodDescription>) beanClassDescription.getDeclaredMethods().filter(named(methodName));

            if (matchingMethods.size() == 1) { //method was declared on class
                return MethodInvocation.invoke(matchingMethods.getOnly());
            }
            if (matchingMethods.isEmpty()) { //method was not found on class, try super class
                return invocationOperation(annotatedMember, beanClassDescription.getSuperClass());
            }
            else { //should never happen
                throw new IllegalStateException("Could not find definition of method: " + methodName);
            }
        }
    }

    private static class SingleMethodStackManipulationSupplier<T extends OptimizedBeanPropertyWriter<T>> implements
            SinglePropStackManipulationSupplier<T> {

        private final TypeDescription beanClassDescription;
        private final MethodReturn methodReturn;

        SingleMethodStackManipulationSupplier(TypeDescription beanClassDescription, MethodReturn methodReturn) {
            this.beanClassDescription = beanClassDescription;
            this.methodReturn = methodReturn;
        }

        private static Map<Integer, SingleMethodStackManipulationSupplier> cache
                = new HashMap<Integer, SingleMethodStackManipulationSupplier>();

        @SuppressWarnings("unchecked")
        static <G extends OptimizedBeanPropertyWriter<G>> SingleMethodStackManipulationSupplier<G> of(
                TypeDescription beanClassDescription, MethodReturn methodReturn) {

            final int key = beanClassDescription.hashCode() + methodReturn.hashCode();
            SingleMethodStackManipulationSupplier result = cache.get(key);
            if (result == null) {
                result = new SingleMethodStackManipulationSupplier(beanClassDescription, methodReturn);
                cache.put(key, result);
            }
            return result;
        }

        @Override
        public StackManipulation supply(T prop) {
            return new SingleMethodStackManipulation<T>(beanClassDescription, prop, methodReturn);
        }
    }

    private static class MethodAppender<T extends OptimizedBeanPropertyWriter<T>> extends AbstractDelegatingAppender<T> {

        private final TypeDescription beanClassDescription;
        private final List<T> props;
        private final MethodReturn methodReturn;

        MethodAppender(TypeDescription beanClassDescription, List<T> props, MethodReturn methodReturn){
            super(props);
            this.beanClassDescription = beanClassDescription;
            this.props = props;
            this.methodReturn = methodReturn;
        }

        @Override
        protected StackManipulation usingSwitch() {

            return new UsingSwitchStackManipulation<T>(
                    LocalVarIndexCalculator.INSTANCE,
                    props,
                    SingleMethodStackManipulationSupplier.<T>of(beanClassDescription, methodReturn)
            );
        }

        @Override
        protected StackManipulation createLocalVar() {
            return CreateLocalVarStackManipulation.of(beanClassDescription);
        }

        @Override
        protected StackManipulation usingIf() {

            return new UsingIfStackManipulation<>(
                    LocalVarIndexCalculator.INSTANCE,
                    props,
                    SingleMethodStackManipulationSupplier.<T>of(beanClassDescription, methodReturn)
            );
        }

        @Override
        protected StackManipulation single() {
            return new SingleMethodStackManipulation<T>(beanClassDescription, props.get(0), methodReturn);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MethodAppender<?> that = (MethodAppender<?>) o;

            if (!beanClassDescription.equals(that.beanClassDescription)) return false;
            if (!props.equals(that.props)) return false;
            return methodReturn == that.methodReturn;
        }

        @Override
        public int hashCode() {
            int result = beanClassDescription.hashCode();
            result = 31 * result + props.hashCode();
            result = 31 * result + methodReturn.hashCode();
            return result;
        }
    }

    private <T extends OptimizedBeanPropertyWriter<T>> DynamicType.Builder<?> _addGetters(
            DynamicType.Builder<?> builder, List<T> props, String methodName, MethodReturn methodReturn) {

        return builder.method(named(methodName))
                      .intercept(
                              new Implementation.Simple(
                                      new MethodAppender<T>(beanClassDefinition, props, methodReturn)
                              )
                      );
    }
    
    /*
    /**********************************************************
    /* Code generation; field-based getters
    /**********************************************************
     */

    private static class SingleFieldStackManipulation<T extends OptimizedBeanPropertyWriter<T>>
            extends AbstractSinglePropStackManipulation<T> {

        SingleFieldStackManipulation(TypeDescription beanClassDescription,
                                             T prop,
                                             MethodReturn methodReturn) {
            super(beanClassDescription, prop, methodReturn);
        }

        @Override
        protected StackManipulation invocationOperation(
                AnnotatedMember annotatedMember, TypeDefinition beanClassDescription) {

            final String fieldName = annotatedMember.getName();
            @SuppressWarnings("unchecked")
            final FieldList<FieldDescription> matchingFields =
                    (FieldList<FieldDescription>) beanClassDescription.getDeclaredFields().filter(named(fieldName));

            if (matchingFields.size() == 1) { //method was declared on class
                return FieldAccess.forField(matchingFields.getOnly()).read();
            }
            if (matchingFields.isEmpty()) { //method was not found on class, try super class
                return invocationOperation(annotatedMember, beanClassDescription.getSuperClass());
            }
            else { //should never happen
                throw new IllegalStateException("Could not find definition of field: " + fieldName);
            }
        }
    }

    private static class SingleFieldStackManipulationSupplier<T extends OptimizedBeanPropertyWriter<T>> implements
            SinglePropStackManipulationSupplier<T> {

        private final TypeDescription beanClassDescription;
        private final MethodReturn methodReturn;

        SingleFieldStackManipulationSupplier(TypeDescription beanClassDescription, MethodReturn methodReturn) {
            this.beanClassDescription = beanClassDescription;
            this.methodReturn = methodReturn;
        }

        private static Map<Integer, SingleFieldStackManipulationSupplier<?>> cache
                = new HashMap<Integer, SingleFieldStackManipulationSupplier<?>>();

        @SuppressWarnings("unchecked")
        static <G extends OptimizedBeanPropertyWriter<G>> SingleFieldStackManipulationSupplier<G> of(
                TypeDescription beanClassDescription, MethodReturn methodReturn) {

            final int key = beanClassDescription.hashCode() + methodReturn.hashCode();
            SingleFieldStackManipulationSupplier<G> result = (SingleFieldStackManipulationSupplier<G>) cache.get(key);
            if (result == null) {
                result = new SingleFieldStackManipulationSupplier<>(beanClassDescription, methodReturn);
                cache.put(key, result);
            }
            return result;
        }

        @Override
        public StackManipulation supply(T prop) {
            return new SingleFieldStackManipulation<T>(beanClassDescription, prop, methodReturn);
        }
    }

    private static class FieldAppender<T extends OptimizedBeanPropertyWriter<T>> extends AbstractDelegatingAppender<T> {

        private final TypeDescription beanClassDescription;
        private final List<T> props;
        private final MethodReturn methodReturn;

        FieldAppender(TypeDescription beanClassDescription, List<T> props, MethodReturn methodReturn){
            super(props);
            this.beanClassDescription = beanClassDescription;
            this.props = props;
            this.methodReturn = methodReturn;
        }

        @Override
        protected StackManipulation usingSwitch() {

            return new UsingSwitchStackManipulation<T>(
                    LocalVarIndexCalculator.INSTANCE,
                    props,
                    SingleFieldStackManipulationSupplier.<T>of(beanClassDescription, methodReturn)
            );
        }

        @Override
        protected StackManipulation createLocalVar() {
            return CreateLocalVarStackManipulation.of(beanClassDescription);
        }

        @Override
        protected StackManipulation usingIf() {

            return new UsingIfStackManipulation<>(
                    LocalVarIndexCalculator.INSTANCE,
                    props,
                    SingleFieldStackManipulationSupplier.<T>of(beanClassDescription, methodReturn)
            );
        }

        @Override
        protected StackManipulation single() {
            return new SingleFieldStackManipulation<T>(beanClassDescription, props.get(0), methodReturn);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MethodAppender<?> that = (MethodAppender<?>) o;

            if (!beanClassDescription.equals(that.beanClassDescription)) return false;
            if (!props.equals(that.props)) return false;
            return methodReturn == that.methodReturn;
        }

        @Override
        public int hashCode() {
            int result = beanClassDescription.hashCode();
            result = 31 * result + props.hashCode();
            result = 31 * result + methodReturn.hashCode();
            return result;
        }
    }

    private <T extends OptimizedBeanPropertyWriter<T>> DynamicType.Builder<?> _addFields(
            DynamicType.Builder<?> builder, List<T> props, String methodName, MethodReturn methodReturn) {

        return builder.method(named(methodName))
                .intercept(
                        new Implementation.Simple(
                                new FieldAppender<T>(beanClassDefinition, props, methodReturn)
                        )
                );
    }
}
