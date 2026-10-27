package tools.jackson.module.blackbird.codegen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import tools.jackson.core.SerializableString;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.PropertyWriter;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.module.blackbird.codegen.ReaderCodegen.ViewStrategy;
import tools.jackson.module.blackbird.internal.GeneratedWriterBase;

/**
 * Defines the hidden-class serializer for one bean: assembles the class data
 * (pre-encoded names, stock writers, child writers and getter handles, as
 * named constants), has {@code BeanWriterGenerator} emit the class bytes, and
 * defines the class in this package. Property values load through constant
 * (Object)eV MethodHandles (see {@link MemberHandles}), so the generated
 * bytecode never names the bean class.
 */
public final class WriterCodegen
{
    public enum WKind { STRING, INT, LONG, BOOLEAN, CHILD, STOCK }

    // handle loads the property value from the bean, erased to (Object)eV
    // (a getter call or a field read); null for STOCK.
    public record GenWProp(WKind kind, PropertyWriter stock,
            SerializableString name, GeneratedWriterBase child, MethodHandle handle) {}

    /**
     * Everything the bytecode emitter needs; the String arrays hold the
     * class-data entry name per property ({@code null} where a property has
     * no such entry). nativePolyWrites: emit a serializeWithType override
     * matching stock BeanSerializerBase's WritableTypeId flow; false (a
     * {@code @JsonTypeId} property exists, whose value feeds the type id)
     * keeps the base forwarding.
     */
    record Plan(String className, List<GenWProp> props, String[] stockName,
            String[] nameName, String[] childName, String[] selfName, String[] handleName,
            ViewStrategy views, boolean includeByDefault, boolean nativePolyWrites) {}

    /**
     * The binary name of the class that holds the write helpers of all
     * generated writers. It is defined once in this package, before the
     * first writer, and the writers call it by name.
     */
    static final String HELPERS_CLASS = WriterCodegen.class.getPackageName() + ".WriteHelpers";

    private WriterCodegen() {}

    /**
     * Generates and defines the writer, or returns {@code null} when the
     * generated code would be too large for the JIT to compile (see
     * {@link ClassFileEmitters#MAX_METHOD_BYTES}).
     */
    @SuppressWarnings("unchecked")
    public static ValueSerializer<Object> generate(Class<?> beanClass, List<GenWProp> props,
            BeanSerializerBase fallback, ViewStrategy views, boolean includeByDefault,
            boolean nativePolyWrites)
            throws ReflectiveOperationException {
        Map<String, Object> classData = new LinkedHashMap<>();
        String[] stockName = new String[props.size()];
        String[] nameName = new String[props.size()];
        String[] childName = new String[props.size()];
        String[] selfName = new String[props.size()];
        String[] handleName = new String[props.size()];
        StringJoiner layout = new StringJoiner(",");
        for (int i = 0; i < props.size(); i++) {
            GenWProp p = props.get(i);
            String base = p.stock().getName();
            layout.add(base + "=" + p.kind());
            stockName[i] = ReaderCodegen.dataName(classData, base + "Writer");
            classData.put(stockName[i], p.stock());
            if (p.name() != null) {
                nameName[i] = ReaderCodegen.dataName(classData, base + "Name");
                classData.put(nameName[i], p.name());
            }
            if (p.child() != null) {
                childName[i] = ReaderCodegen.dataName(classData, base + "Codec");
                classData.put(childName[i], p.child());
                selfName[i] = ReaderCodegen.dataName(classData, base + "Self");
                classData.put(selfName[i], GeneratedWriterBase.selfReferenceWriter(
                        (BeanPropertyWriter) p.stock()));
            }
            if (p.handle() != null) {
                handleName[i] = ReaderCodegen.dataName(classData, base + "Get");
                classData.put(handleName[i], p.handle());
            }
        }

        HelpersHolder.ensureDefined();
        MethodHandles.Lookup definer = MethodHandles.lookup();
        // A hidden class must be named in its define context's package.
        String className = definer.lookupClass().getPackageName()
                + ".BBWriter_" + ReaderCodegen.codecName(beanClass);
        byte[] bytes = ClassFileEmitters.emit(new Plan(className, props, stockName, nameName,
                childName, selfName, handleName, views, includeByDefault, nativePolyWrites));
        if (bytes == null) {
            CodegenDebug.log(() -> "writer gate: a generated method is over "
                    + ClassFileEmitters.MAX_METHOD_BYTES + " bytes");
            return null;
        }
        CodegenDump.dump(beanClass, "writer", bytes);
        // Module-context defines never legitimately fail: any throw here is a
        // generator bug, surfaced through the modifier's failure handling.
        MethodHandles.Lookup hidden = definer.defineHiddenClassWithClassData(
                bytes, Map.copyOf(classData), true);
        MethodHandle ctor = hidden.findConstructor(hidden.lookupClass(),
                MethodType.methodType(void.class, BeanSerializerBase.class, String.class));
        try {
            return (ValueSerializer<Object>) ctor.invoke(fallback, layout.toString());
        } catch (Throwable t) {
            throw new IllegalStateException("cannot instantiate generated writer", t);
        }
    }

    // A normal class, not a hidden one, so that the hidden writers can name
    // it. It is defined at most once per class loader of Blackbird (a second
    // define of the same name would fail), on first use rather than in a
    // static initializer: a failed attempt (say, a throwable from deep in a
    // recursive resolution) must not leave the class unusable for every
    // later writer, so the next generation tries again.
    private static final class HelpersHolder
    {
        private static volatile Class<?> _defined;

        static void ensureDefined() throws IllegalAccessException {
            if (_defined == null) {
                define();
            }
        }

        private static synchronized void define() throws IllegalAccessException {
            if (_defined == null) {
                byte[] bytes = ClassFileEmitters.emitWriteHelpers();
                CodegenDump.dump(HELPERS_CLASS, bytes);
                try {
                    _defined = MethodHandles.lookup().defineClass(bytes);
                } catch (LinkageError e) {
                    // An earlier attempt may have defined the class and then
                    // failed before recording it: use that definition.
                    _defined = _alreadyDefined(e);
                }
            }
        }

        private static Class<?> _alreadyDefined(LinkageError defineFailure) {
            try {
                return Class.forName(HELPERS_CLASS, false, WriterCodegen.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                defineFailure.addSuppressed(e);
                throw defineFailure;
            }
        }
    }
}
