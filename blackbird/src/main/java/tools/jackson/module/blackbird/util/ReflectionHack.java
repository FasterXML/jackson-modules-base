package tools.jackson.module.blackbird.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
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

    static class Java9Up {
        static final MethodHandle FACTORY;

        static {
            FACTORY = init();
        }

        private static MethodHandle init() {
            try {
                return MethodHandles.lookup().findStatic(
                        MethodHandles.class,
                        "privateLookupIn",
                        MethodType.methodType(Lookup.class, Class.class, MethodHandles.Lookup.class));
            } catch (ReflectiveOperationException e) {
                Logger.getLogger(BlackbirdModule.class.getName()).log(Level.WARNING,
                        "Unable to find Java 9+ MethodHandles.privateLookupIn.  "
                        + "Blackbird is not performing optimally!"
                        , e);
                return null;
            }
        }

        public static Lookup privateLookupIn(Class<?> lookup, Lookup orig) {
            return Unchecked.supplier(() -> (Lookup) FACTORY.invokeExact(lookup, orig)).get();
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
