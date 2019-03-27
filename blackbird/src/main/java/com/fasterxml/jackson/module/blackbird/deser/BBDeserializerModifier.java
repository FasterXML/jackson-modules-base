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

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.*;
import com.fasterxml.jackson.databind.deser.impl.MethodProperty;
import com.fasterxml.jackson.databind.deser.std.StdValueInstantiator;
import com.fasterxml.jackson.databind.introspect.*;
import com.fasterxml.jackson.databind.util.ClassUtil;

public class BBDeserializerModifier extends BeanDeserializerModifier
{
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
    private Function<Class<?>, Lookup> _lookups;

    public BBDeserializerModifier(Function<Class<?>, MethodHandles.Lookup> lookups)
    {
        _lookups = lookups;
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
            lookup = MethodHandles.privateLookupIn(beanClass, lookup);
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
        List<OptimizedSettableBeanProperty<?>> newProps = findOptimizableProperties(
                lookup, config, builder.getProperties());
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
            if (inst.canCreateUsingDefault()) {
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
        ArrayList<OptimizedSettableBeanProperty<?>> newProps = new ArrayList<OptimizedSettableBeanProperty<?>>();

        // Ok, then, find any properties for which we could generate accessors
        while (propIterator.hasNext()) {
            try {
                nextProperty(propIterator.next(), lookup, newProps);
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
            Lookup lookup,
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
        if (jdkMember instanceof Method && prop instanceof MethodProperty) {
            final Method method = (Method) jdkMember;
            setter = lookup.unreflect(method);
            type = ((AnnotatedMethod) member).getRawParameterType(0);
        } else {
            return;
            //setter = lookup.unreflectGetter((Field) jdkMember);
        }

        if (type.isPrimitive()) {
            if (type == Integer.TYPE) {
                newProps.add(new SettableIntProperty(prop,
                    createSetter(lookup, ObjIntConsumer.class, ObjIntBiFunction.class,
                        INT_TRAMPOLINE, int.class, setter)));
            } else if (type == Long.TYPE) {
                newProps.add(new SettableLongProperty(prop,
                    createSetter(lookup, ObjLongConsumer.class, ObjLongBiFunction.class,
                        LONG_TRAMPOLINE, long.class, setter)));
            } else if (type == Boolean.TYPE) {
                newProps.add(new SettableBooleanProperty(prop,
                    createSetter(lookup, ObjBooleanConsumer.class, ObjBooleanBiFunction.class,
                        BOOLEAN_TRAMPOLINE, boolean.class, setter)));
            }
        } else {
            if (type == String.class) {
                newProps.add(new SettableStringProperty(prop,
                    createSetter(lookup, BiConsumer.class, BiFunction.class, TRAMPOLINE, Object.class, setter)));
            } else {
                newProps.add(new SettableObjectProperty(prop,
                    createSetter(lookup, BiConsumer.class, BiFunction.class, TRAMPOLINE, Object.class, setter)));
            }
        }
    }

    private <T> T createSetter(Lookup lookup, Class<T> iface, Class<?> thunkType, MethodHandle trampoline, Class<?> valueType, MethodHandle setter)
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
        Object builtThunk = LambdaMetafactory.metafactory(
                lookup,
                "apply",
                MethodType.methodType(thunkType),
                MethodType.methodType(Object.class, Object.class, valueType),
                setter,
                setter.type())
            .getTarget().invoke();
        return iface.cast(LambdaMetafactory.metafactory(
                MethodHandles.lookup(),
                "accept",
                MethodType.methodType(iface, thunkType),
                MethodType.methodType(void.class, Object.class, Object.class),
                trampoline,
                MethodType.methodType(void.class, Object.class, valueType))
            .getTarget().invoke(builtThunk));
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
