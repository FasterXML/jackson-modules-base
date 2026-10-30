package tools.jackson.module.blackbird.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.Logger;

import tools.jackson.module.blackbird.BlackbirdModule;

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
    @SuppressWarnings("synthetic-access")
    public static Lookup privateLookupIn(Class<?> lookup, MethodHandles.Lookup orig) throws IllegalAccessException {
        if (Java9Up.FACTORY != null) {
            return Java9Up.privateLookupIn(lookup, orig);
        }
        return Java8.privateLookupIn(lookup);
    }

    /**
     * Whether an accessor spun with {@code lookup} can call {@code handle}, unreflected
     * from {@code method}, without failing on every call. It casts to each parameter
     * type (including the receiver), so those must be accessible from the lookup
     * class; and the call names every type in the method's signature, so those must
     * also be the same classes from the lookup class's loader (see
     * {@link #isSameClassWithoutLoading}).
     */
    public static boolean canLinkAccessor(Lookup lookup, Method method, MethodHandle handle) {
        final MethodType type = handle.type();
        final Class<?> declaringClass = method.getDeclaringClass();
        final boolean sameLoader;
        try {
            sameLoader = (declaringClass.getClassLoader() == lookup.lookupClass().getClassLoader());
        } catch (SecurityException e) {
            return false;
        }
        // Reflection resolved the method's signature through its declaring class's
        // loader; if that's the lookup class's loader, the accessor resolves the same
        // classes, so only access needs checking
        if (sameLoader) {
            for (Class<?> paramType : type.parameterArray()) {
                if (!isAccessible(lookup, paramType)) {
                    return false;
                }
            }
            return true;
        }
        // For an inherited protected method the handle's receiver is narrowed to the
        // lookup class, but on Java 8 calling it still names the declaring class (which
        // is otherwise the receiver, checked below). Newer JDKs don't name or load it,
        // so only check it there, and without loading it.
        if ((Java9Up.FACTORY == null)
                && (type.parameterCount() > 0) && (type.parameterType(0) != declaringClass)
                && !isSameClassWithoutLoading(lookup, declaringClass)) {
            return false;
        }
        for (Class<?> paramType : type.parameterArray()) {
            if (!canAccessClass(lookup, paramType)) {
                return false;
            }
        }
        // The return type isn't cast to, but the JVM still requires it to be the same
        // class from the accessor's loader. The accessor never loads it, so neither
        // may this check.
        return isSameClassWithoutLoading(lookup, type.returnType());
    }

    /**
     * Whether code defined with {@code lookup}, such as a lambda it spins, can use
     * {@code type} where it must also access it, as in a cast: it must be the same
     * class from the lookup class's loader (see {@link #isSameClassWithoutLoading})
     * and accessible from it. If not, that code fails when it runs, not when it is
     * created.
     */
    public static boolean canAccessClass(Lookup lookup, Class<?> type) {
        return isSameClassWithoutLoading(lookup, type) && isAccessible(lookup, type);
    }

    // Whether `type` is accessible from the lookup class, wherever it was loaded
    private static boolean isAccessible(Lookup lookup, Class<?> type) {
        type = elementType(type);
        if (type.isPrimitive()) {
            return true;
        }
        try {
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
     * Whether {@code type}, as code defined with {@code lookup} refers to it, is known
     * to be the same class from the lookup class's loader, without asking that loader.
     * Asking could make it define its own copy, which can't be undone and breaks code
     * that relies on it never doing so. So only a class that loader defined itself
     * qualifies, or a {@code java.*} one (which only the JDK's loaders can define);
     * types from other loaders, even JDK {@code javax.*} ones, don't.
     */
    public static boolean isSameClassWithoutLoading(Lookup lookup, Class<?> type) {
        type = elementType(type);
        if (type.isPrimitive() || type.getName().startsWith("java.")) {
            return true;
        }
        try {
            return type.getClassLoader() == lookup.lookupClass().getClassLoader();
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
