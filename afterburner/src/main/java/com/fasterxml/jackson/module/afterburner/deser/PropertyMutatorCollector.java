package com.fasterxml.jackson.module.afterburner.deser;

import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
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
import net.bytebuddy.implementation.bytecode.assign.TypeCasting;
import net.bytebuddy.implementation.bytecode.member.FieldAccess;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.implementation.bytecode.member.MethodReturn;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import net.bytebuddy.jar.asm.MethodVisitor;

import java.lang.reflect.Method;
import java.util.*;

import static net.bytebuddy.description.type.TypeDescription.ForLoadedType;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Simple collector used to keep track of properties for which code-generated
 * mutators are needed.
 */
public class PropertyMutatorCollector
    extends DynamicPropertyAccessorBase
{
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
    private final TypeDescription beanClassDefinition;

    public PropertyMutatorCollector(Class<?> beanClass) {
        this.beanClass = beanClass;
        this.beanClassDefinition = new ForLoadedType(beanClass);
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
        DynamicType.Builder<?> builder =
                new ByteBuddy(ClassFileVersion.JAVA_V6)
                        .with(TypeValidation.DISABLED)
                        .subclass(BeanPropertyMutator.class, ConstructorStrategy.Default.DEFAULT_CONSTRUCTOR)
                        .name(baseName.getSlashedTemplate())
                        .modifiers(Visibility.PUBLIC, TypeManifestation.FINAL);


        // and then add various accessors; first field accessors:
        if (!_intFields.isEmpty()) {
            builder = _addFields(builder, _intFields, "intField", MethodVariableAccess.INTEGER);
        }
        if (!_longFields.isEmpty()) {
            builder = _addFields(builder, _longFields, "longField", MethodVariableAccess.LONG);
        }
        if (!_booleanFields.isEmpty()) {
            // booleans are simply ints 0 and 1
            builder = _addFields(builder, _booleanFields, "booleanField", MethodVariableAccess.INTEGER);
        }
        if (!_stringFields.isEmpty()) {
            builder = _addFields(builder, _stringFields, "stringField", MethodVariableAccess.REFERENCE);
        }
        if (!_objectFields.isEmpty()) {
            builder = _addFields(builder, _objectFields, "objectField", MethodVariableAccess.REFERENCE);
        }

        // and then method accessors:
        if (!_intSetters.isEmpty()) {
            builder = _addSetters(builder, _intSetters, "intSetter", MethodVariableAccess.INTEGER);
        }
        if (!_longSetters.isEmpty()) {
            builder = _addSetters(builder, _longSetters, "longSetter", MethodVariableAccess.LONG);
        }
        if (!_booleanSetters.isEmpty()) {
            // booleans are simply ints 0 and 1
            builder = _addSetters(builder, _booleanSetters, "booleanSetter", MethodVariableAccess.INTEGER);
        }
        if (!_stringSetters.isEmpty()) {
            builder = _addSetters(builder, _stringSetters, "stringSetter", MethodVariableAccess.REFERENCE);
        }
        if (!_objectSetters.isEmpty()) {
            builder = _addSetters(builder, _objectSetters, "objectSetter", MethodVariableAccess.REFERENCE);
        }

        byte[] bytecode = builder.make().getBytes();
        baseName.assignChecksum(bytecode);
        // already defined exactly as-is?

        try {
            return classLoader.loadClass(baseName.getDottedName());
        } catch (ClassNotFoundException e) { }
        // if not, load, resolve etc:
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
    private static class LocalVarIndexCalculator implements AbstractPropertyStackManipulation.LocalVarIndexCalculator {

        private final MethodVariableAccess beanValueAccess;

        LocalVarIndexCalculator(MethodVariableAccess beanValueAccess) {
            this.beanValueAccess = beanValueAccess;
        }

        private static Map<MethodVariableAccess, LocalVarIndexCalculator> cache = new HashMap<>();

        public static LocalVarIndexCalculator of(MethodVariableAccess beanValueAccess) {
            LocalVarIndexCalculator result = cache.get(beanValueAccess);
            if (result == null) {
                result = new LocalVarIndexCalculator(beanValueAccess);
                cache.put(beanValueAccess, result);
            }
            return result;
        }

        //we have 3 arguments (plus arg 0 which is 'this'), so 4 is the position of the first unless it's a long
        //this would look a lot nicer if MethodVariableAccess.stackSize were public...
        @Override
        public int calculate() {
            return 3 + (beanValueAccess == MethodVariableAccess.LONG ?  2 : 1);
        }
    }

    private static class CreateLocalVarStackManipulation extends AbstractCreateLocalVarStackManipulation {

        CreateLocalVarStackManipulation(TypeDescription beanClassDescription,
                                                MethodVariableAccess beanValueAccess) {
            super(beanClassDescription, PropertyMutatorCollector.LocalVarIndexCalculator.of(beanValueAccess));
        }

        private static Map<Integer, CreateLocalVarStackManipulation> cache
                = new HashMap<Integer, CreateLocalVarStackManipulation>();

        static CreateLocalVarStackManipulation of(TypeDescription beanClassDescription,
                MethodVariableAccess beanValueAccess) {

            final int key = beanClassDescription.hashCode() + beanValueAccess.hashCode();
            CreateLocalVarStackManipulation result = cache.get(key);
            if (result == null) {
                result = new CreateLocalVarStackManipulation(beanClassDescription, beanValueAccess);
                cache.put(key, result);
            }
            return result;
        }
    }

    /**
     * Adds bytecode for operation like:
     * <pre>
     * {@code
     * var4.setA(var3);
     * }
     * <pre/>
     */
    private abstract static class AbstractSinglePropStackManipulation<T extends OptimizedSettableBeanProperty<T>>
            extends AbstractPropertyStackManipulation {
        private final TypeDescription beanClassDescription;
        private final T prop;
        private final MethodVariableAccess beanValueAccess;

        AbstractSinglePropStackManipulation(TypeDescription beanClassDescription,
                                            T prop,
                                            MethodVariableAccess beanValueAccess) {
            super(PropertyMutatorCollector.LocalVarIndexCalculator.of(beanValueAccess));
            this.beanValueAccess = beanValueAccess;
            this.beanClassDescription = beanClassDescription;
            this.prop = prop;
        }

        abstract protected Class<?> getClassToCastBeanValueTo(AnnotatedMember annotatedMember);
        abstract protected StackManipulation invocationOperation(
                AnnotatedMember annotatedMember, TypeDefinition beanClassDesc);

        @Override
        public Size apply(MethodVisitor methodVisitor,
                          Implementation.Context implementationContext) {

            final boolean mustCast = (beanValueAccess == MethodVariableAccess.REFERENCE);

            final List<StackManipulation> operations = new ArrayList<StackManipulation>();
            operations.add(loadLocalVar()); // load local for cast bean
            operations.add(loadBeanValueArg());

            final AnnotatedMember member = prop.getMember();
            if (mustCast) {
                operations.add(TypeCasting.to(new ForLoadedType(getClassToCastBeanValueTo(member))));
            }

            operations.add(invocationOperation(member, beanClassDescription));
            operations.add(MethodReturn.VOID);

            final StackManipulation.Compound compound = new StackManipulation.Compound(operations);
            return compound.apply(methodVisitor, implementationContext);
        }

        private StackManipulation loadLocalVar() {
            return MethodVariableAccess.REFERENCE.loadFrom(localVarIndex());
        }

        private StackManipulation loadBeanValueArg() {
            return beanValueAccess.loadFrom(beanValueArgIndex());
        }

        /**
         * we know that all methods of created in {@link PropertyMutatorCollector} contain the bean value as the 3rd arg
         */
        private int beanValueArgIndex() {
            return 3;
        }
    }

    /*
    /**********************************************************
    /* Code generation; field-based
    /**********************************************************
     */
    private static class SingleFieldStackManipulation<T extends OptimizedSettableBeanProperty<T>>
            extends AbstractSinglePropStackManipulation<T> {

        SingleFieldStackManipulation(TypeDescription beanClassDescription,
                T prop, MethodVariableAccess beanValueAccess) {
            super(beanClassDescription, prop, beanValueAccess);
        }

        @Override
        protected Class<?> getClassToCastBeanValueTo(AnnotatedMember annotatedMember) {
            return annotatedMember.getRawType();
        }

        @SuppressWarnings("unchecked")
        @Override
        protected StackManipulation invocationOperation(AnnotatedMember annotatedMember,
                TypeDefinition beanClassDescription) {

            final String fieldName = annotatedMember.getName();
            final FieldList<FieldDescription> matchingFields =
                    (FieldList<FieldDescription>) beanClassDescription.getDeclaredFields().filter(named(fieldName));

            if (matchingFields.size() == 1) { //method was declared on class
                return FieldAccess.forField(matchingFields.getOnly()).write();
            }
            if (matchingFields.isEmpty()) { //method was not found on class, try super class
                return invocationOperation(annotatedMember, beanClassDescription.getSuperClass());
            }
            else { //should never happen
                throw new IllegalStateException("Could not find definition of field: " + fieldName);
            }
        }
    }

    private static class SingleFieldStackManipulationSupplier<T extends OptimizedSettableBeanProperty<T>> implements
            SinglePropStackManipulationSupplier<T> {

        private final TypeDescription beanClassDescription;
        private final MethodVariableAccess beanValueAccess;

        SingleFieldStackManipulationSupplier(TypeDescription beanClassDescription,
                                                   MethodVariableAccess beanValueAccess) {
            this.beanClassDescription = beanClassDescription;
            this.beanValueAccess = beanValueAccess;
        }

        private static Map<Integer, SingleFieldStackManipulationSupplier<?>> cache
                = new HashMap<Integer, SingleFieldStackManipulationSupplier<?>>();

        @SuppressWarnings("unchecked")
        static <G extends OptimizedSettableBeanProperty<G>> SingleFieldStackManipulationSupplier<G> of(
                TypeDescription beanClassDescription, MethodVariableAccess beanValueAccess) {

            final int key = beanClassDescription.hashCode() + beanValueAccess.hashCode();
            SingleFieldStackManipulationSupplier<G> result = (SingleFieldStackManipulationSupplier<G>) cache.get(key);
            if (result == null) {
                result = new SingleFieldStackManipulationSupplier<G>(beanClassDescription, beanValueAccess);
                cache.put(key, result);
            }
            return result;
        }

        @Override
        public StackManipulation supply(T prop) {
            return new SingleFieldStackManipulation<T>(beanClassDescription, prop, beanValueAccess);
        }
    }

    private static class FieldAppender<T extends OptimizedSettableBeanProperty<T>> extends AbstractDelegatingAppender<T> {

        private final TypeDescription beanClassDescription;
        private final List<T> props;
        private final MethodVariableAccess beanValueAccess;

        FieldAppender(TypeDescription beanClassDescription, List<T> props, MethodVariableAccess beanValueAccess){
            super(props);
            this.beanClassDescription = beanClassDescription;
            this.props = props;
            this.beanValueAccess = beanValueAccess;
        }

        @Override
        protected StackManipulation usingSwitch() {

            return new UsingSwitchStackManipulation<T>(
                    LocalVarIndexCalculator.of(beanValueAccess),
                    props,
                    SingleFieldStackManipulationSupplier.<T>of(beanClassDescription, beanValueAccess)
            );
        }

        @Override
        protected StackManipulation createLocalVar() {
            return CreateLocalVarStackManipulation.of(beanClassDescription, beanValueAccess);
        }

        @Override
        protected StackManipulation usingIf() {

            return new UsingIfStackManipulation<T>(
                    LocalVarIndexCalculator.of(beanValueAccess),
                    props,
                    SingleFieldStackManipulationSupplier.<T>of(beanClassDescription, beanValueAccess)
            );
        }

        @Override
        protected StackManipulation single() {
            return new SingleFieldStackManipulation<T>(beanClassDescription, props.get(0), beanValueAccess);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FieldAppender<?> that = (FieldAppender<?>) o;

            if (!beanClassDescription.equals(that.beanClassDescription)) return false;
            if (!props.equals(that.props)) return false;
            return beanValueAccess == that.beanValueAccess;
        }

        @Override
        public int hashCode() {
            int result = beanClassDescription.hashCode();
            result = 31 * result + props.hashCode();
            result = 31 * result + beanValueAccess.hashCode();
            return result;
        }
    }

    private <T extends OptimizedSettableBeanProperty<T>> DynamicType.Builder<?> _addFields(
            DynamicType.Builder<?> builder, List<T> props, String methodName, MethodVariableAccess beanValueAccess) {

        return builder.method(named(methodName))
                      .intercept(
                              new Implementation.Simple(
                                      new FieldAppender<T>(beanClassDefinition, props, beanValueAccess)
                              )
                      );
    }
    
    /*
    /**********************************************************
    /* Code generation; method-based
    /**********************************************************
     */

    private static class SingleMethodStackManipulationSupplier<T extends OptimizedSettableBeanProperty<T>> implements
            SinglePropStackManipulationSupplier<T> {

        private final TypeDescription beanClassDescription;
        private final MethodVariableAccess beanValueAccess;

        SingleMethodStackManipulationSupplier(TypeDescription beanClassDescription,
                                                    MethodVariableAccess beanValueAccess) {
            this.beanClassDescription = beanClassDescription;
            this.beanValueAccess = beanValueAccess;
        }

        private static Map<Integer, SingleMethodStackManipulationSupplier<?>> cache
                = new HashMap<Integer, SingleMethodStackManipulationSupplier<?>>();

        static <G extends OptimizedSettableBeanProperty<G>> SingleMethodStackManipulationSupplier<G> of(
                TypeDescription beanClassDescription, MethodVariableAccess beanValueAccess) {

            final int key = beanClassDescription.hashCode() + beanValueAccess.hashCode();
            @SuppressWarnings("unchecked")
            SingleMethodStackManipulationSupplier<G> result = (SingleMethodStackManipulationSupplier<G>) cache.get(key);
            if (result == null) {
                result = new SingleMethodStackManipulationSupplier<G>(beanClassDescription, beanValueAccess);
                cache.put(key, result);
            }
            return result;
        }

        @Override
        public StackManipulation supply(T prop) {
            return new SingleMethodStackManipulation<T>(beanClassDescription, prop, beanValueAccess);
        }
    }

    private static class SingleMethodStackManipulation<T extends OptimizedSettableBeanProperty<T>>
            extends AbstractSinglePropStackManipulation<T> {

        SingleMethodStackManipulation(TypeDescription beanClassDescription,
                                      T prop,
                                      MethodVariableAccess beanValueAccess) {
            super(beanClassDescription, prop, beanValueAccess);
        }

        @Override
        protected Class<?> getClassToCastBeanValueTo(AnnotatedMember annotatedMember) {
            final Method method = (Method) (annotatedMember.getMember());
            return method.getParameterTypes()[0];
        }

        @Override
        protected StackManipulation invocationOperation(AnnotatedMember annotatedMember,
                TypeDefinition beanClassDescription) {

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


    private static class MethodAppender<T extends OptimizedSettableBeanProperty<T>> extends AbstractDelegatingAppender<T> {

        private final TypeDescription beanClassDescription;
        private final List<T> props;
        private final MethodVariableAccess beanValueAccess;

        MethodAppender(TypeDescription beanClassDescription,
                              List<T> props,
                              MethodVariableAccess beanValueAccess) {
            super(props);
            this.beanClassDescription = beanClassDescription;
            this.props = props;
            this.beanValueAccess = beanValueAccess;
        }

        @Override
        protected StackManipulation createLocalVar() {
            return CreateLocalVarStackManipulation.of(beanClassDescription, beanValueAccess);
        }

        @Override
        protected StackManipulation usingSwitch() {

            return new UsingSwitchStackManipulation<T>(
                    LocalVarIndexCalculator.of(beanValueAccess),
                    props,
                    SingleMethodStackManipulationSupplier.<T>of(beanClassDescription, beanValueAccess)
            );
        }

        @Override
        protected StackManipulation usingIf() {

            return new UsingIfStackManipulation<T>(
                    LocalVarIndexCalculator.of(beanValueAccess),
                    props,
                    SingleMethodStackManipulationSupplier.<T>of(beanClassDescription, beanValueAccess)
            );
        }

        @Override
        protected StackManipulation single() {
            return new SingleMethodStackManipulation<T>(beanClassDescription, props.get(0), beanValueAccess);
        }
    }

    private <T extends OptimizedSettableBeanProperty<T>> DynamicType.Builder<?> _addSetters(
            DynamicType.Builder<?> builder, List<T> props, String methodName, MethodVariableAccess beanValueAccess) {

        return builder.method(named(methodName))
                      .intercept(
                              new Implementation.Simple(
                                      new MethodAppender<T>(beanClassDefinition, props, beanValueAccess)
                              )
                      );
    }

}
