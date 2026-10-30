package com.fasterxml.jackson.module.blackbird.deser;

import java.lang.invoke.LambdaConversionException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.*;
import com.fasterxml.jackson.databind.deser.impl.MethodProperty;
import com.fasterxml.jackson.databind.deser.std.StdValueInstantiator;
import com.fasterxml.jackson.databind.introspect.*;
import com.fasterxml.jackson.databind.util.ClassUtil;
import com.fasterxml.jackson.module.blackbird.util.ReflectionHack;

public class BBDeserializerModifier extends BeanDeserializerModifier
{
    private static final long serialVersionUID = 1L;

    private static final Logger LOG = Logger.getLogger(BBDeserializerModifier.class.getName());

    private static final MethodHandle TRAMPOLINE, BOOLEAN_TRAMPOLINE, LONG_TRAMPOLINE, INT_TRAMPOLINE;

    static {
        try {
            TRAMPOLINE = MethodHandles.lookup().findStatic(BBDeserializerModifier.class, "trampoline",
                    MethodType.methodType(void.class, BiFunction.class, Object.class, Object.class));
            BOOLEAN_TRAMPOLINE = MethodHandles.lookup().findStatic(BBDeserializerModifier.class, "booleanTrampoline",
                MethodType.methodType(void.class, ObjBooleanBiFunction.class, Object.class, boolean.class));
            LONG_TRAMPOLINE = MethodHandles.lookup().findStatic(BBDeserializerModifier.class, "longTrampoline",
                    MethodType.methodType(void.class, ObjLongBiFunction.class, Object.class, long.class));
            INT_TRAMPOLINE = MethodHandles.lookup().findStatic(BBDeserializerModifier.class, "intTrampoline",
                    MethodType.methodType(void.class, ObjIntBiFunction.class, Object.class, int.class));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    private final Function<Class<?>, Lookup> _lookups;
    private final UnaryOperator<Lookup> _accessGrant;

    public BBDeserializerModifier(Function<Class<?>, MethodHandles.Lookup> lookups, UnaryOperator<MethodHandles.Lookup> accessGrant)
    {
        _lookups = lookups;
        _accessGrant = accessGrant;
    }

    /*
    /**********************************************************************
    /* BeanDeserializerModifier methods
    /**********************************************************************
     */

    @Override
    public BeanDeserializerBuilder updateBuilder(DeserializationConfig config,
            BeanDescription beanDesc, BeanDeserializerBuilder builder)
    {
        final Class<?> beanClass = beanDesc.getBeanClass();
        MethodHandles.Lookup lookup = _lookups.apply(beanClass);
        if (lookup == null) {
            return builder;
        }
        try {
            lookup = ReflectionHack.privateLookupIn(beanClass, lookup);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        /* Hmmh. Can we access stuff from private classes?
         * Possibly, if we can use parent class loader.
         * (should probably skip all non-public?)
         */
        if (Modifier.isPrivate(beanClass.getModifiers())) { // TODO??
            return builder;
        }
        lookup = _accessGrant.apply(lookup);
        // databind sets these for a builder's deserializer before this; a builder
        // without a build method (`buildMethodName=""`) has the config
        final boolean forBuilder = (builder.getBuildMethod() != null)
                || (builder.getBuilderConfig() != null);
        List<OptimizedSettableBeanProperty<?>> newProps = forBuilder
                ? findOptimizableProperties(lookup, config, builder.getProperties(), true)
                : findOptimizableProperties(lookup, config, builder.getProperties());
        // and if we found any, create mutator proxy, replace property objects
        if (!newProps.isEmpty()) {
            for (OptimizedSettableBeanProperty<?> prop : newProps) {
                builder.addOrReplaceProperty(prop, true);
            }
        }
        // Second thing: see if we could (re)generate Creator(s):
        ValueInstantiator inst = builder.getValueInstantiator();
        /* Hmmh. Probably better to require exact default implementation
         * and not sub-class; chances are sub-class uses its own
         * construction anyway.
         */
        if (inst.getClass() == StdValueInstantiator.class) {
            // also, only override if using default creator (no-arg ctor, no-arg static factory)
            if (inst.canCreateUsingDefault() || inst.canCreateFromObjectWith()) {
                inst = new CreatorOptimizer(beanClass, lookup, (StdValueInstantiator) inst).createOptimized();
                if (inst != null) {
                    builder.setValueInstantiator(inst);
                }
            }
        }

        // also: may want to replace actual BeanDeserializer as well? For this, need to replace builder
        // (but only if builder is the original standard one; don't want to break other impls)
        if (builder.getClass() == BeanDeserializerBuilder.class) {
            return new SuperSonicDeserializerBuilder(builder);
        }
        return builder;
    }

    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */

    protected List<OptimizedSettableBeanProperty<?>> findOptimizableProperties(
            Lookup lookup, DeserializationConfig config,
            Iterator<SettableBeanProperty> propIterator)
    {
        return findOptimizableProperties(lookup, config, propIterator, false);
    }

    protected List<OptimizedSettableBeanProperty<?>> findOptimizableProperties(
            Lookup lookup, DeserializationConfig config,
            Iterator<SettableBeanProperty> propIterator, boolean forBuilder)
    {
        ArrayList<OptimizedSettableBeanProperty<?>> newProps = new ArrayList<OptimizedSettableBeanProperty<?>>();

        // Ok, then, find any properties for which we could generate accessors
        while (propIterator.hasNext()) {
            try {
                nextProperty(propIterator.next(), lookup, config, forBuilder, newProps);
            } catch (Throwable e) {
                if (e instanceof Error) {
                    throw (Error) e;
                }
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException(e);
            }
        }
        return newProps;
    }

    @SuppressWarnings("unchecked")
    private void nextProperty(SettableBeanProperty prop,
            Lookup lookup, DeserializationConfig config, boolean forBuilder,
            ArrayList<OptimizedSettableBeanProperty<?>> newProps) throws Throwable
    {
        AnnotatedMember member = prop.getMember();
        Member jdkMember = member.getMember();

        // if we ever support virtual properties, this would be null, so check, skip
        if (jdkMember == null) {
            return;
        }
        // First: we can't access private fields or methods....
        if (Modifier.isPrivate(jdkMember.getModifiers())) {
            return;
        }
        // (although, interestingly enough, can seem to access private classes...)

        // 30-Jul-2012, tatu: [module-afterburner#6]: Needs to skip custom deserializers, if any.
        if (prop.hasValueDeserializer()) {
            if (!isDefaultDeserializer(prop.getValueDeserializer())) {
                return;
            }
        }

        MethodHandle setter;
        Class<?> type;
        boolean callableByReflection;
        if (jdkMember instanceof Method && prop instanceof MethodProperty) {
            final Method method = (Method) jdkMember;
            try {
                setter = lookup.unreflect(_withoutAccessOverride(method));
            } catch (IllegalAccessException | NoSuchMethodException | SecurityException e) {
                // e.g. an inherited protected setter, where the lookup class isn't the
                // subclass (as with `CrossLoaderAccess` on some JDKs); reflection can still call it
                _logSkipped(method, lookup, e);
                return;
            }
            type = ((AnnotatedMethod) member).getRawParameterType(0);
            // Only these get a generated setter, so don't check (or load) anything for others
            if (type.isPrimitive() && (type != Integer.TYPE) && (type != Long.TYPE) && (type != Boolean.TYPE)) {
                return;
            }
            // A generated setter that would fail on every call (like one taking a
            // package-private type from another package) is left to reflection
            if (!ReflectionHack.canLinkAccessor(lookup, method, setter)) {
                _logSkipped(method, lookup, null);
                return;
            }
            // Builder setters used to go through reflection, which applies Java's
            // access rules (modules included) unless Jackson made the setter accessible;
            // the generated setter ignores those rules, so it may only call what
            // reflection could. `setAndReturn` is otherwise left to reflection, and a
            // builder's setter altogether, as databind also calls `set()` on builders
            callableByReflection = isMadeAccessible(config, method)
                    || (Modifier.isPublic(method.getModifiers())
                            && ReflectionHack.isPubliclyAccessible(method.getDeclaringClass()));
            if (forBuilder && !callableByReflection) {
                return;
            }
        } else {
            return;
            //setter = lookup.unreflectGetter((Field) jdkMember);
        }

        final Class<?> receiverType = setter.type().parameterType(0);

        if (type.isPrimitive()) {
            if (type == Integer.TYPE) {
                ObjIntBiFunction thunk = createThunk(lookup, ObjIntBiFunction.class, int.class, setter);
                newProps.add(new SettableIntProperty(prop,
                    createSetter(lookup, ObjIntConsumer.class, ObjIntBiFunction.class,
                        INT_TRAMPOLINE, int.class, setter, thunk),
                    callableByReflection ? thunk : null, receiverType, forBuilder));
            } else if (type == Long.TYPE) {
                ObjLongBiFunction thunk = createThunk(lookup, ObjLongBiFunction.class, long.class, setter);
                newProps.add(new SettableLongProperty(prop,
                    createSetter(lookup, ObjLongConsumer.class, ObjLongBiFunction.class,
                        LONG_TRAMPOLINE, long.class, setter, thunk),
                    callableByReflection ? thunk : null, receiverType, forBuilder));
            } else if (type == Boolean.TYPE) {
                ObjBooleanBiFunction thunk = createThunk(lookup, ObjBooleanBiFunction.class, boolean.class, setter);
                newProps.add(new SettableBooleanProperty(prop,
                    createSetter(lookup, ObjBooleanConsumer.class, ObjBooleanBiFunction.class,
                        BOOLEAN_TRAMPOLINE, boolean.class, setter, thunk),
                    callableByReflection ? thunk : null, receiverType, forBuilder));
            }
        } else {
            BiFunction<Object, Object, Object> thunk = createThunk(lookup, BiFunction.class, Object.class, setter);
            if (type == String.class) {
                newProps.add(new SettableStringProperty(prop,
                    createSetter(lookup, BiConsumer.class, BiFunction.class, TRAMPOLINE, Object.class, setter, thunk),
                    callableByReflection ? thunk : null, receiverType, forBuilder));
            } else {
                newProps.add(new SettableObjectProperty(prop,
                    createSetter(lookup, BiConsumer.class, BiFunction.class, TRAMPOLINE, Object.class, setter, thunk),
                    callableByReflection ? thunk : null, receiverType, forBuilder));
            }
        }
    }

    // Whether Jackson makes the setter accessible, as `ClassUtil.checkAndFixAccess`
    // does for properties (and only if allowed to override access modifiers)
    static boolean isMadeAccessible(DeserializationConfig config, Method setter) {
        if (!config.canOverrideAccessModifiers()) {
            return false;
        }
        Class<?> declaringClass = setter.getDeclaringClass();
        boolean isPublic = Modifier.isPublic(setter.getModifiers())
                && Modifier.isPublic(declaringClass.getModifiers());
        return !isPublic
                || (config.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS)
                        && !ClassUtil.isJDKClass(declaringClass));
    }

    // Returns null for void setters, which have nothing to return
    private <T> T createThunk(Lookup lookup, Class<T> thunkType, Class<?> valueType, MethodHandle setter)
            throws Throwable, LambdaConversionException {
        if (setter.type().returnType() == void.class) {
            return null;
        }
        return thunkType.cast(LambdaMetafactory.metafactory(
                lookup,
                "apply",
                MethodType.methodType(thunkType),
                MethodType.methodType(Object.class, Object.class, valueType),
                setter,
                setter.type())
            .getTarget().invoke());
    }

    private <T> T createSetter(Lookup lookup, Class<T> iface, Class<?> thunkType, MethodHandle trampoline, Class<?> valueType, MethodHandle setter,
            Object builtThunk)
            throws Throwable, LambdaConversionException {
        if (setter.type().returnType() == void.class) {
            return iface.cast(LambdaMetafactory.metafactory(
                    lookup,
                    "accept",
                    MethodType.methodType(iface),
                    MethodType.methodType(void.class, Object.class, valueType),
                    setter,
                    setter.type())
                    .getTarget().invoke());
        }
        return iface.cast(LambdaMetafactory.metafactory(
                MethodHandles.lookup(),
                "accept",
                MethodType.methodType(iface, thunkType),
                MethodType.methodType(void.class, Object.class, valueType),
                trampoline,
                MethodType.methodType(void.class, Object.class, valueType))
            .getTarget().invoke(builtThunk));
    }

    // `unreflect` skips its access checks for a method made accessible (as databind
    // does early for injection or back references), but the generated accessor
    // can't skip them, so check a copy that isn't
    @SuppressWarnings("deprecation")
    private static Method _withoutAccessOverride(Method method) throws NoSuchMethodException {
        if (!method.isAccessible()) {
            return method;
        }
        return method.getDeclaringClass().getDeclaredMethod(method.getName(), method.getParameterTypes());
    }

    private static void _logSkipped(Method setter, Lookup lookup, Throwable cause) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, String.format(
                    "Not optimizing setter %s: a generated accessor in %s %s; using reflection",
                    setter, lookup.lookupClass().getName(),
                    (cause == null) ? "couldn't link to it" : "can't access it"), cause);
        }
    }

    /**
     * Helper method used to check whether given deserializer is the default
     * deserializer implementation: this is necessary to avoid overriding other
     * kinds of deserializers.
     */
    protected boolean isDefaultDeserializer(JsonDeserializer<?> deser) {
        return ClassUtil.isJacksonStdImpl(deser)
                // 07-May-2018, tatu: Probably can't happen but just in case
                || (deser instanceof SuperSonicBeanDeserializer);

    }

    // These trampolines adapt BiFunction-like setters (i.e. returns a value)
    // to BiConsumer style through a generated thunk lambda.

    @FunctionalInterface
    public interface ObjIntBiFunction {
        Object apply(Object bean, int value);
    }

    @FunctionalInterface
    public interface ObjLongBiFunction {
        Object apply(Object bean, long value);
    }

    @FunctionalInterface
    public interface ObjBooleanBiFunction {
        Object apply(Object bean, boolean value);
    }

    static void intTrampoline(ObjIntBiFunction thunk, Object bean, int value) {
        thunk.apply(bean, value);
    }

    static void longTrampoline(ObjLongBiFunction thunk, Object bean, long value) {
        thunk.apply(bean, value);
    }

    static void booleanTrampoline(ObjBooleanBiFunction thunk, Object bean, boolean value) {
        thunk.apply(bean, value);
    }

    static void trampoline(BiFunction<Object, Object, Object> thunk, Object bean, Object value) {
        thunk.apply(bean, value);
    }
}
