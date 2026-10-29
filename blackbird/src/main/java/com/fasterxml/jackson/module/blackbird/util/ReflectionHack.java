package com.fasterxml.jackson.module.blackbird.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.module.blackbird.BlackbirdModule;

import java.lang.invoke.MethodType;

/**
 * Allow private access to fields across using a JVM version-appropriate strategy
 * without having a compile-time dependency on Java 9+.
 *
 * This entire class can be replaced by a direct call to JDK 9+
 * {@link MethodHandles}{@code .privateLookupIn}
 * once Jackson targets Java >= 9.
 */
public class ReflectionHack {
    public static Lookup privateLookupIn(Class<?> lookup, MethodHandles.Lookup orig) throws IllegalAccessException {
        if (Java9Up.FACTORY != null) {
            return Java9Up.privateLookupIn(lookup, orig);
        }
        return Java8.privateLookupIn(lookup);
    }

    /**
     * Whether code defined with {@code lookup}, such as a lambda it spins, can use
     * {@code type} where it must also access it, as in a cast. If not, that code
     * fails when it runs, not when it is created.
     */
    public static boolean canAccessClass(Lookup lookup, Class<?> type) {
        type = elementType(type);
        if (type.isPrimitive()) {
            return true;
        }
        try {
            if (!resolvesTo(lookup, type)) {
                return false;
            }
            if (Java9Up.ACCESS_CLASS != null) {
                return Java9Up.canAccessClass(lookup, type);
            }
            return canAccessClassWithoutModules(lookup.lookupClass(), type);
        } catch (SecurityException e) {
            // e.g. a security manager denying `getClassLoader`
            return false;
        }
    }

    /**
     * Like {@link #canAccessClass}, for a type that code only names, as in the
     * descriptor of a method it calls: it must resolve to the same class, but
     * need not be accessible.
     */
    public static boolean canResolveClass(Lookup lookup, Class<?> type) {
        type = elementType(type);
        if (type.isPrimitive()) {
            return true;
        }
        try {
            return resolvesTo(lookup, type);
        } catch (SecurityException e) {
            return false;
        }
    }

    private static Class<?> elementType(Class<?> type) {
        while (type.isArray()) {
            type = type.getComponentType();
        }
        return type;
    }

    // That code refers to `type` by name, resolved by the lookup class's loader,
    // which may find a different class of the same name (e.g. a child-first loader
    // with its own copy) or none at all
    private static boolean resolvesTo(Lookup lookup, Class<?> type) {
        try {
            return Class.forName(type.getName(), false, lookup.lookupClass().getClassLoader()) == type;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    // Without modules, public classes are accessible from anywhere, others only
    // from the same runtime package (which includes the bean's own class, often
    // package-private). `getModifiers` reports the declared modifiers of nested
    // classes, so a protected one is (safely) treated as inaccessible from other
    // packages, though the JVM would allow it.
    static boolean canAccessClassWithoutModules(Class<?> lookupClass, Class<?> type) {
        if (Modifier.isPublic(type.getModifiers())) {
            return true;
        }
        return (type.getClassLoader() == lookupClass.getClassLoader())
                && packageName(type).equals(packageName(lookupClass));
    }

    private static String packageName(Class<?> type) {
        String name = type.getName();
        int dot = name.lastIndexOf('.');
        return (dot < 0) ? "" : name.substring(0, dot);
    }

    static class Java9Up {
        static final MethodHandle FACTORY;
        static final MethodHandle ACCESS_CLASS;

        static {
            FACTORY = init();
            ACCESS_CLASS = (FACTORY == null) ? null : initAccessClass();
        }

        private static MethodHandle init() {
            try {
                return MethodHandles.lookup().findStatic(
                        MethodHandles.class,
                        "privateLookupIn",
                        MethodType.methodType(Lookup.class, Class.class, MethodHandles.Lookup.class));
            } catch (ReflectiveOperationException e) {
                Logger l = Logger.getLogger(BlackbirdModule.class.getName());
                // 02-Nov-2022, tatu: as per [modules-base#187] only log exception at trace
                l.log(Level.WARNING,
                        "Unable to find Java 9+ MethodHandles.privateLookupIn.  Blackbird is not performing optimally!");
                l.log(Level.FINE,
                        "Failure reason for `MethodHandles.privateLookupIn()` not being found: "+e.getMessage(), e);
                return null;
            }
        }

        private static MethodHandle initAccessClass() {
            try {
                return MethodHandles.lookup().findVirtual(
                        MethodHandles.Lookup.class,
                        "accessClass",
                        MethodType.methodType(Class.class, Class.class));
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }

        public static Lookup privateLookupIn(Class<?> lookup, Lookup orig) {
            try {
                return (Lookup) FACTORY.invokeExact(lookup, orig);
            } catch (ReflectiveOperationException e) {
                return orig;
            } catch (Throwable t) {
                throw Sneaky.throwAnyway(t);
            }
        }

        public static boolean canAccessClass(Lookup lookup, Class<?> type) {
            try {
                Class<?> unused = (Class<?>) ACCESS_CLASS.invokeExact(lookup, type);
                return true;
            } catch (IllegalAccessException e) {
                return false;
            } catch (Throwable t) {
                throw Sneaky.throwAnyway(t);
            }
        }
    }

    static class Java8 {
        private static final Constructor<MethodHandles.Lookup> FACTORY;

        static {
            try {
                FACTORY = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(
                        "Unable to use private Lookup constructor",
                        e);
            }

            if (!FACTORY.isAccessible()) {
                FACTORY.setAccessible(true);
            }
        }

        public static Lookup privateLookupIn(Class<?> lookup) {
            return Unchecked.function(FACTORY::newInstance).apply(lookup);
        }
    }
}
