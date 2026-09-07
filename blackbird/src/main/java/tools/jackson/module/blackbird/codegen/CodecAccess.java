package tools.jackson.module.blackbird.codegen;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Modifier;
import java.util.function.Function;

/**
 * Access decisions shared by the deserializer and serializer factories: where
 * a generated hidden class may be defined, and which members its bytecode may
 * reference directly.
 */
public final class CodecAccess
{
    private CodecAccess() {}

    /**
     * Public beans keep the module's define context (returned as null);
     * non-public beans need the bean's package context, reached through
     * privateLookupIn with the user-supplied lookup (or the module's own,
     * which suffices on the classpath where everything shares the unnamed
     * module). No reachable context means the bean stays on the stock path.
     */
    public static MethodHandles.Lookup defineContext(Class<?> beanClass,
            Function<Class<?>, MethodHandles.Lookup> lookups) {
        if (Modifier.isPublic(beanClass.getModifiers())
                || beanClass.getPackageName().isEmpty()) {
            return null;
        }
        MethodHandles.Lookup caller = lookups.apply(beanClass);
        if (caller == null) {
            caller = MethodHandles.lookup();
        }
        try {
            return MethodHandles.privateLookupIn(beanClass, caller);
        } catch (IllegalAccessException | SecurityException e) {
            return null;
        }
    }

    /**
     * A member the generated code may reference directly: public on a public
     * class always, and otherwise a non-private member of a class in the same
     * runtime package as the define context (anchor null = module context).
     */
    public static boolean directlyAccessible(int memberMods, Class<?> declaring,
            Class<?> anchor) {
        if (Modifier.isPublic(memberMods) && Modifier.isPublic(declaring.getModifiers())) {
            return true;
        }
        return anchor != null
                && !Modifier.isPrivate(memberMods)
                && !Modifier.isPrivate(declaring.getModifiers())
                && declaring.getPackageName().equals(anchor.getPackageName())
                && declaring.getClassLoader() == anchor.getClassLoader();
    }
}
