package tools.jackson.module.blackbird.codegen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Member access for generated code: every member is unreflected and adapted
 * to an erased shape (primitives kept, references widened to Object), so
 * generated descriptors never name a bean class. Databind calls fixAccess on
 * every member before any modifier runs, and unreflection of a member whose
 * accessible flag is set does no access check, so this reaches exactly what
 * stock databind can invoke - private members, non-public classes, foreign
 * classloaders, and JPMS packages open only to tools.jackson.databind
 * included. Unreflection uses the public lookup, so this class grants no
 * access of its own: a member nobody made accessible unreflects only when it
 * is public in an exported package. An IllegalAccessException here means
 * databind could not open the member either (CAN_OVERRIDE_ACCESS_MODIFIERS
 * disabled, or a module not open to databind); the caller demotes to the
 * stock path, which fails the same way at first use.
 */
public final class MemberHandles
{
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

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

    /** Field store adapted to {@code (Object, eV)void}. */
    public static MethodHandle fieldSetter(Field field) throws IllegalAccessException {
        return LOOKUP.unreflectSetter(field)
                .asType(MethodType.methodType(void.class, Object.class, erase(field.getType())));
    }

    /** No-arg constructor adapted to {@code ()Object}. */
    public static MethodHandle defaultConstructor(Constructor<?> ctor)
            throws IllegalAccessException {
        return LOOKUP.unreflectConstructor(ctor)
                .asType(MethodType.methodType(Object.class));
    }
}
