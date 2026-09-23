package tools.jackson.module.blackbird.codegen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Member access for generated code: every member is unreflected with the
 * module's own lookup and adapted to an erased shape (primitives kept,
 * references widened to Object), so generated descriptors never name a bean
 * class. Databind calls fixAccess on every member before any modifier runs,
 * and unreflection of a member whose accessible flag is set does no access
 * check, so this reaches exactly what stock databind can invoke - private
 * members, non-public classes, foreign classloaders, and JPMS packages open
 * only to tools.jackson.databind included. An IllegalAccessException here
 * means databind could not open the member either (CAN_OVERRIDE_ACCESS_MODIFIERS
 * disabled, or a module not open to databind); the caller demotes to the
 * stock path, which fails the same way at first use.
 */
public final class MemberHandles
{
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private MemberHandles() {}

    public static Class<?> erase(Class<?> type) {
        return type.isPrimitive() ? type : Object.class;
    }

    /** Setter adapted to {@code (Object, eV)void}; a fluent return is discarded. */
    public static MethodHandle setter(Method setter, Class<?> valueType)
            throws IllegalAccessException {
        return LOOKUP.unreflect(setter)
                .asType(MethodType.methodType(void.class, Object.class, erase(valueType)));
    }

    /**
     * Builder setter adapted to {@code (Object, eV)Object} returning the
     * builder to keep: the fluent return when there is one, the receiver
     * otherwise, so generated code applies every setter uniformly.
     */
    public static MethodHandle builderSetter(Method setter, Class<?> valueType)
            throws IllegalAccessException {
        MethodHandle raw = LOOKUP.unreflect(setter);
        Class<?> ev = erase(valueType);
        if (setter.getReturnType() == void.class) {
            MethodHandle keepReceiver = MethodHandles.dropArguments(
                    MethodHandles.identity(Object.class), 1, ev);
            return MethodHandles.foldArguments(keepReceiver,
                    raw.asType(MethodType.methodType(void.class, Object.class, ev)));
        }
        return raw.asType(MethodType.methodType(Object.class, Object.class, ev));
    }

    /** Field store adapted to {@code (Object, eV)void}. */
    public static MethodHandle fieldSetter(Field field) throws IllegalAccessException {
        return LOOKUP.unreflectSetter(field)
                .asType(MethodType.methodType(void.class, Object.class, erase(field.getType())));
    }

    /** Getter adapted to {@code (Object)eV}. */
    public static MethodHandle getter(Method getter) throws IllegalAccessException {
        return LOOKUP.unreflect(getter).asType(
                MethodType.methodType(erase(getter.getReturnType()), Object.class));
    }

    /** Field load adapted to {@code (Object)eV}. */
    public static MethodHandle fieldGetter(Field field) throws IllegalAccessException {
        return LOOKUP.unreflectGetter(field)
                .asType(MethodType.methodType(erase(field.getType()), Object.class));
    }

    /** No-arg constructor adapted to {@code ()Object}. */
    public static MethodHandle defaultConstructor(Constructor<?> ctor)
            throws IllegalAccessException {
        return LOOKUP.unreflectConstructor(ctor)
                .asType(MethodType.methodType(Object.class));
    }

    /** Constructor adapted to {@code (e1..en)Object}. */
    public static MethodHandle constructor(Constructor<?> ctor) throws IllegalAccessException {
        MethodHandle raw = LOOKUP.unreflectConstructor(ctor);
        Class<?>[] params = ctor.getParameterTypes();
        Class<?>[] erased = new Class<?>[params.length];
        for (int i = 0; i < params.length; i++) {
            erased[i] = erase(params[i]);
        }
        return raw.asType(MethodType.methodType(Object.class, erased));
    }

    /** Method adapted to {@code (Object)Object} (build methods). */
    public static MethodHandle unary(Method method) throws IllegalAccessException {
        return LOOKUP.unreflect(method)
                .asType(MethodType.methodType(Object.class, Object.class));
    }
}
