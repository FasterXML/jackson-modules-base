package tools.jackson.module.blackbird.codegen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Locates the class-file emitters, {@code BeanReaderGenerator} and
 * {@code BeanWriterGenerator}, and the shared write helpers that
 * {@code BeanWriterGenerator} emits. They use the ClassFile API
 * ({@code java.lang.classfile}) and compile at release 25 into
 * {@code META-INF/versions/25} of the multi-release jar, so the rest of the
 * module can stay at release 17. On an older runtime the JVM does not see
 * them, and Blackbird cannot generate codecs (see
 * {@code BlackbirdModule.setupModule}). The base classes find them by name
 * because they cannot reference them at compile time.
 */
public final class ClassFileEmitters
{
    /**
     * The largest generated method, in bytecode bytes, that the emitters
     * return; a larger class comes back as {@code null} and the bean keeps
     * the stock (de)serializer. HotSpot does not JIT-compile methods over
     * 8000 bytes ({@code -XX:+DontCompileHugeMethods}, the default), and an
     * interpreted codec is several times slower than stock; the margin
     * leaves room for the HotSpot limit to be counted differently.
     */
    public static final int MAX_METHOD_BYTES = 7000;

    private static final MethodHandle READER = find("BeanReaderGenerator", "emit",
            MethodType.methodType(byte[].class, ReaderCodegen.Plan.class));
    private static final MethodHandle WRITER = find("BeanWriterGenerator", "emit",
            MethodType.methodType(byte[].class, WriterCodegen.Plan.class));
    private static final MethodHandle WRITE_HELPERS = find("BeanWriterGenerator",
            "emitWriteHelpers", MethodType.methodType(byte[].class));

    private ClassFileEmitters() {}

    /**
     * Whether this runtime sees the release-25 emitters: false on Java 17 to
     * 24, and on any Java version when the jar was repackaged without its
     * {@code META-INF/versions/25} entries.
     */
    public static boolean available() {
        return (READER != null) && (WRITER != null) && (WRITE_HELPERS != null);
    }

    // Both return null when a generated method is over MAX_METHOD_BYTES.
    static byte[] emit(ReaderCodegen.Plan plan) {
        return invoke(READER, plan);
    }

    static byte[] emit(WriterCodegen.Plan plan) {
        return invoke(WRITER, plan);
    }

    // The class WriterCodegen.HELPERS_CLASS, never over MAX_METHOD_BYTES.
    static byte[] emitWriteHelpers() {
        return invoke(WRITE_HELPERS);
    }

    private static byte[] invoke(MethodHandle emitter, Object... args) {
        if (emitter == null) {
            throw new IllegalStateException("Blackbird class-file emitters are not available"
                    + " on this runtime");
        }
        try {
            return (byte[]) emitter.invokeWithArguments(args);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    // A class from META-INF/versions/25 is absent on Java 17-24
    // (ClassNotFoundException); if a repackaged jar moved it to the base
    // directory, loading it there fails with UnsupportedClassVersionError.
    private static MethodHandle find(String simpleName, String method, MethodType type) {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            Class<?> emitter = lookup.findClass(
                    ClassFileEmitters.class.getPackageName() + "." + simpleName);
            return lookup.findStatic(emitter, method, type);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
