package tools.jackson.module.blackbird.codegen;

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

    public static void log(String message) {
        if (ENABLED) {
            System.err.println("bbdebug " + message);
        }
    }
}
