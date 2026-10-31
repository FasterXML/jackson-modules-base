package tools.jackson.module.blackbird.codegen;

import java.util.function.Supplier;

/**
 * The debug switches for code generation, read once. {@link #CODEGEN_PROPERTY}
 * enables the generation trace on stderr (gate decisions, generated class
 * names); {@link #DUMP_DIR_PROPERTY} names a directory that receives every
 * generated class file (see {@link CodegenDump}).
 */
public final class CodegenDebug
{
    public static final String CODEGEN_PROPERTY = "blackbird.debug.codegen";
    public static final String DUMP_DIR_PROPERTY = "blackbird.debug.dumpDir";

    public static final boolean ENABLED = Boolean.getBoolean(CODEGEN_PROPERTY);

    private CodegenDebug() {}

    // A supplier, so that no caller builds its message while tracing is off.
    public static void log(Supplier<String> message) {
        if (ENABLED) {
            System.err.println("bbdebug " + message.get());
        }
    }

    // A constant message: nothing to defer.
    public static void log(String message) {
        if (ENABLED) {
            System.err.println("bbdebug " + message);
        }
    }

    /**
     * Traces a whole-bean demotion. The modifiers see every type the mapper
     * resolves, and the great majority are scalars, collections and other JDK
     * types that could never be beans; tracing those buries the one bean the
     * trace is being read for, so they are left out.
     */
    public static void logSkip(String side, Class<?> beanClass, String reason) {
        if (ENABLED && !isBulkType(beanClass)) {
            log(() -> side + " skip " + beanClass.getName() + ": " + reason);
        }
    }

    private static boolean isBulkType(Class<?> type) {
        if (type.isPrimitive() || type.isArray() || type.isEnum()) {
            return true;
        }
        String name = type.getName();
        return name.startsWith("java.") || name.startsWith("javax.")
                || name.startsWith("jdk.") || name.startsWith("sun.");
    }
}
