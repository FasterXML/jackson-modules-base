package com.fasterxml.jackson.module.blackbird.ser;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JacksonStdImpl;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.ser.*;
import com.fasterxml.jackson.databind.util.ClassUtil;

public class BBSerializerModifier extends BeanSerializerModifier
{
    private final Function<Class<?>, Lookup> _lookups;

    public BBSerializerModifier(Function<Class<?>, MethodHandles.Lookup> lookups)
    {
        this._lookups = lookups;
    }

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
            BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties)
    {
        final Class<?> beanClass = beanDesc.getBeanClass();

        /* Hmmh. Can we access stuff from private classes?
         * Possibly, if we can use parent class loader.
         * (should probably skip all non-public?)
         */
        if (Modifier.isPrivate(beanClass.getModifiers())) { // TODO?
            return beanProperties;
        }

        findProperties(beanClass, config, beanProperties);
        return beanProperties;
    }

    protected void findProperties(Class<?> beanClass,
            SerializationConfig config, List<BeanPropertyWriter> beanProperties)
    {
        MethodHandles.Lookup lookup = _lookups.apply(beanClass);
        if (lookup == null ) {
            return;
        }

        ListIterator<BeanPropertyWriter> it = beanProperties.listIterator();
        while (it.hasNext()) {
            try {
                createProperty(it, MethodHandles.privateLookupIn(beanClass, lookup), config);
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
    }

    protected void createProperty(ListIterator<BeanPropertyWriter> it, Lookup lookup, SerializationConfig config) throws Throwable {
        BeanPropertyWriter bpw = it.next();
        AnnotatedMember member = bpw.getMember();

        Member jdkMember = member.getMember();
        // 11-Sep-2015, tatu: Let's skip virtual members (related to #57)
        if (jdkMember == null) {
            return;
        }
        // We can't access private fields or methods, skip:
        if (Modifier.isPrivate(jdkMember.getModifiers())) {
            return;
        }
        // (although, interestingly enough, can seem to access private classes...)

        // 30-Jul-2012, tatu: [#6]: Needs to skip custom serializers, if any.
        if (bpw.hasSerializer()) {
            if (!isDefaultSerializer(config, bpw.getSerializer())) {
                return;
            }
        }
        // [#9]: also skip unwrapping stuff...
        if (bpw.isUnwrapping()) {
            return;
        }

        if (!bpw.getClass().isAnnotationPresent(JacksonStdImpl.class)) {
            return;
        }

        Class<?> type = bpw.getMember().getRawType();
        MethodHandle getter;
        if (member instanceof AnnotatedMethod) {
            getter = lookup.unreflect((Method) member.getMember());
        } else {
            return;
            //getter = lookup.unreflectGetter((Field) member.getMember());
        }

        if (type.isPrimitive()) {
            if (type == Integer.TYPE) {
                ToIntFunction<Object> accessor = (ToIntFunction<Object>) LambdaMetafactory.metafactory(
                        lookup,
                        "applyAsInt",
                        MethodType.methodType(ToIntFunction.class),
                        MethodType.methodType(int.class, Object.class),
                        getter,
                        getter.type())
                    .getTarget().invokeExact();
                it.set(new IntPropertyWriter(bpw, accessor, null));
            } else if (type == Long.TYPE) {
                ToLongFunction<Object> accessor = (ToLongFunction<Object>) LambdaMetafactory.metafactory(
                        lookup,
                        "applyAsLong",
                        MethodType.methodType(ToLongFunction.class),
                        MethodType.methodType(long.class, Object.class),
                        getter,
                        getter.type())
                    .getTarget().invokeExact();
                it.set(new LongPropertyWriter(bpw, accessor, null));
            } else if (type == Boolean.TYPE) {
                ToBooleanFunction accessor = (ToBooleanFunction) LambdaMetafactory.metafactory(
                        lookup,
                        "applyAsBoolean",
                        MethodType.methodType(ToBooleanFunction.class),
                        MethodType.methodType(boolean.class, Object.class),
                        getter,
                        getter.type())
                    .getTarget().invokeExact();
                it.set(new BooleanPropertyWriter(bpw, accessor, null));
            }
        } else {
            if (type == String.class) {
                Function<Object, String> accessor = (Function<Object, String>) LambdaMetafactory.metafactory(
                        lookup,
                        "apply",
                        MethodType.methodType(Function.class),
                        MethodType.methodType(Object.class, Object.class),
                        getter,
                        getter.type())
                    .getTarget().invokeExact();
                it.set(new StringPropertyWriter(bpw, accessor, null));
            } else { // any other Object types; we can at least call accessor
                Function<Object, Object> accessor = (Function<Object, Object>) LambdaMetafactory.metafactory(
                        lookup,
                        "apply",
                        MethodType.methodType(Function.class),
                        MethodType.methodType(Object.class, Object.class),
                        getter,
                        getter.type())
                    .getTarget().invokeExact();
                it.set(new ObjectPropertyWriter(bpw, accessor, null));
            }
        }
    }

    /**
     * Helper method used to check whether given serializer is the default
     * serializer implementation: this is necessary to avoid overriding other
     * kinds of serializers.
     */
    protected boolean isDefaultSerializer(SerializationConfig config,
            JsonSerializer<?> ser)
    {
        return ClassUtil.isJacksonStdImpl(ser);
    }
}
