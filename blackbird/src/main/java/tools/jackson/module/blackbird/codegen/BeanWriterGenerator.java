package tools.jackson.module.blackbird.codegen;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.attribute.MethodParametersAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.SerializableString;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.PropertyWriter;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.module.blackbird.codegen.BeanReaderGenerator.ViewStrategy;
import tools.jackson.module.blackbird.internal.GeneratedWriterBase;

/**
 * Emits a hidden-class serializer for one bean: a straight-line sequence of
 * writeName/write-value pairs with pre-encoded name constants for eligible
 * properties and the stock PropertyWriter (a named classData constant) for
 * everything else. Beans that declare views resolve a per-view visibility
 * bitmask and each property tests its bit, so view-active writes stay on the
 * generated path; beans with more than 64 properties delegate view-active
 * calls to the stock serializer instead.
 */
public final class BeanWriterGenerator
{
    public enum WKind { STRING, INT, LONG, BOOLEAN, CHILD, STOCK }

    // getter carries a public getter; field carries a public field read
    // through getfield instead. Exactly one of the two is set for a generated
    // (non-STOCK) property.
    public record GenWProp(WKind kind, Method getter, PropertyWriter stock,
            SerializableString name, GeneratedWriterBase child, Field field) {
        public GenWProp(WKind kind, Method getter, PropertyWriter stock,
                SerializableString name, GeneratedWriterBase child) {
            this(kind, getter, stock, name, child, null);
        }
    }

    private static final ClassDesc CD_JSON_GENERATOR = ClassDesc.of("tools.jackson.core.JsonGenerator");
    private static final ClassDesc CD_SER_CONTEXT = ClassDesc.of("tools.jackson.databind.SerializationContext");
    private static final ClassDesc CD_SERIALIZABLE_STRING = ClassDesc.of("tools.jackson.core.SerializableString");
    private static final ClassDesc CD_PROPERTY_WRITER = ClassDesc.of("tools.jackson.databind.ser.PropertyWriter");
    private static final ClassDesc CD_WRITER_BASE =
            GeneratedWriterBase.class.describeConstable().orElseThrow();
    private static final ClassDesc CD_BEAN_SER_BASE = ClassDesc.of("tools.jackson.databind.ser.bean.BeanSerializerBase");

    private static final MethodTypeDesc MTD_SERIALIZE = MethodTypeDesc.of(ConstantDescs.CD_void,
            ConstantDescs.CD_Object, CD_JSON_GENERATOR, CD_SER_CONTEXT);
    private static final MethodTypeDesc MTD_WRITE_NAME =
            MethodTypeDesc.of(CD_JSON_GENERATOR, CD_SERIALIZABLE_STRING);
    private static final MethodTypeDesc MTD_WRITE_STRING =
            MethodTypeDesc.of(CD_JSON_GENERATOR, ConstantDescs.CD_String);
    private static final MethodTypeDesc MTD_WRITE_INT =
            MethodTypeDesc.of(CD_JSON_GENERATOR, ConstantDescs.CD_int);
    private static final MethodTypeDesc MTD_WRITE_LONG =
            MethodTypeDesc.of(CD_JSON_GENERATOR, ConstantDescs.CD_long);
    private static final MethodTypeDesc MTD_WRITE_BOOLEAN =
            MethodTypeDesc.of(CD_JSON_GENERATOR, ConstantDescs.CD_boolean);
    private static final MethodTypeDesc MTD_WRITE_NULL = MethodTypeDesc.of(CD_JSON_GENERATOR);
    private static final MethodTypeDesc MTD_WRITE_START_OBJECT =
            MethodTypeDesc.of(CD_JSON_GENERATOR, ConstantDescs.CD_Object);
    private static final MethodTypeDesc MTD_WRITE_END = MethodTypeDesc.of(CD_JSON_GENERATOR);
    private static final MethodTypeDesc MTD_SERIALIZE_AS_PROPERTY = MethodTypeDesc.of(
            ConstantDescs.CD_void, ConstantDescs.CD_Object, CD_JSON_GENERATOR, CD_SER_CONTEXT);
    private static final MethodTypeDesc MTD_CTOR =
            MethodTypeDesc.of(ConstantDescs.CD_void, CD_BEAN_SER_BASE);
    private static final MethodTypeDesc MTD_GET_ACTIVE_VIEW =
            MethodTypeDesc.of(ConstantDescs.CD_Class);
    private static final MethodTypeDesc MTD_VIEW_MASK =
            MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_Class);
    private static final MethodTypeDesc MTD_PROP_VISIBLE = MethodTypeDesc.of(
            ConstantDescs.CD_boolean, CD_PROPERTY_WRITER, ConstantDescs.CD_Class,
            ConstantDescs.CD_boolean);

    // Named class-data entries through the writer base's classDataEntry
    // bootstrap; rationale in BeanReaderGenerator.
    private static final java.lang.constant.DirectMethodHandleDesc BSM_DATA_ENTRY =
            ConstantDescs.ofConstantBootstrap(CD_WRITER_BASE, "classDataEntry",
                    ConstantDescs.CD_Object);

    private BeanWriterGenerator() {}

    private static void ldcData(CodeBuilder cob, String name, ClassDesc type) {
        cob.ldc(DynamicConstantDesc.ofNamed(BSM_DATA_ENTRY, name, type));
    }

    @SuppressWarnings("unchecked")
    public static ValueSerializer<Object> generate(Class<?> beanClass, List<GenWProp> props,
            BeanSerializerBase fallback, MethodHandles.Lookup defineLookup,
            ViewStrategy views, boolean includeByDefault)
            throws ReflectiveOperationException {
        Map<String, Object> classData = new LinkedHashMap<>();
        String[] stockName = new String[props.size()];
        String[] nameName = new String[props.size()];
        String[] childName = new String[props.size()];
        for (int i = 0; i < props.size(); i++) {
            GenWProp p = props.get(i);
            String base = p.stock().getName();
            stockName[i] = BeanReaderGenerator.dataName(classData, base + "Writer");
            classData.put(stockName[i], p.stock());
            if (p.name() != null) {
                nameName[i] = BeanReaderGenerator.dataName(classData, base + "Name");
                classData.put(nameName[i], p.name());
            }
            if (p.child() != null) {
                childName[i] = BeanReaderGenerator.dataName(classData, base + "Codec");
                classData.put(childName[i], p.child());
            }
        }

        // Rationale in BeanReaderGenerator: non-public beans define in the
        // bean's package context; a define failure there is an environment
        // gate (possible only when the bean's module does not read blackbird).
        MethodHandles.Lookup definer =
                (defineLookup != null) ? defineLookup : MethodHandles.lookup();
        byte[] bytes = buildClass(definer.lookupClass().getPackageName(), beanClass, props,
                stockName, nameName, childName, views, includeByDefault);
        CodegenDump.dump(beanClass, "writer", bytes);
        MethodHandles.Lookup hidden;
        try {
            hidden = definer.defineHiddenClassWithClassData(
                    bytes, Map.copyOf(classData), true);
        } catch (IllegalAccessException | SecurityException | LinkageError e) {
            if (defineLookup == null) {
                if (e instanceof IllegalAccessException iae) {
                    throw iae;
                }
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw (LinkageError) e;
            }
            return null;
        }
        MethodHandle ctor = hidden.findConstructor(hidden.lookupClass(),
                MethodType.methodType(void.class, BeanSerializerBase.class));
        try {
            return (ValueSerializer<Object>) ctor.invoke(fallback);
        } catch (Throwable t) {
            throw new IllegalStateException("cannot instantiate generated writer", t);
        }
    }

    // C2 refuses to inline a hot method whose bytecode exceeds FreqInlineSize
    // (default 325). The scalar helpers are padded past that limit so each
    // compiles standalone: on aarch64, inlined copies of writeName and the
    // NumberOutput digit paths inside a large generated body run 2-3x slower
    // than their standalone compilations, which costs number-heavy shapes ~24%.
    // Out-of-line helper calls measure at parity with the best inlined layout.
    private static final int INLINE_PAD = 384;

    private static final MethodTypeDesc MTD_HELP_STRING = MethodTypeDesc.of(ConstantDescs.CD_void,
            CD_JSON_GENERATOR, CD_SERIALIZABLE_STRING, ConstantDescs.CD_String);
    private static final MethodTypeDesc MTD_HELP_INT = MethodTypeDesc.of(ConstantDescs.CD_void,
            CD_JSON_GENERATOR, CD_SERIALIZABLE_STRING, ConstantDescs.CD_int);
    private static final MethodTypeDesc MTD_HELP_LONG = MethodTypeDesc.of(ConstantDescs.CD_void,
            CD_JSON_GENERATOR, CD_SERIALIZABLE_STRING, ConstantDescs.CD_long);
    private static final MethodTypeDesc MTD_HELP_BOOLEAN = MethodTypeDesc.of(ConstantDescs.CD_void,
            CD_JSON_GENERATOR, CD_SERIALIZABLE_STRING, ConstantDescs.CD_boolean);
    private static final MethodTypeDesc MTD_HELP_NAME = MethodTypeDesc.of(ConstantDescs.CD_void,
            CD_JSON_GENERATOR, CD_SERIALIZABLE_STRING);

    private static byte[] buildClass(String targetPackage, Class<?> beanClass,
            List<GenWProp> props,
            String[] stockName, String[] nameName, String[] childName,
            ViewStrategy views, boolean includeByDefault) {
        // A hidden class must be named in its define context's package.
        ClassDesc thisClass = ClassDesc.of(
                targetPackage + ".BBWriter_" + beanClass.getSimpleName());
        return ClassFile.of().build(thisClass, clb -> {
            clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            clb.withSuperclass(CD_WRITER_BASE);
            clb.withMethod(ConstantDescs.INIT_NAME, MTD_CTOR, ClassFile.ACC_PUBLIC,
                    mb -> mb.with(BeanReaderGenerator.params("fallback"))
                            .withCode(cob -> cob.aload(0).aload(1)
                                    .invokespecial(CD_WRITER_BASE, ConstantDescs.INIT_NAME, MTD_CTOR)
                                    .return_()));
            clb.withMethod("serialize", MTD_SERIALIZE, ClassFile.ACC_PUBLIC,
                    mb -> mb.with(BeanReaderGenerator.params("value", "g", "ctxt"))
                            .withCode(cob -> buildSerialize(cob, thisClass, beanClass, props,
                                    stockName, nameName, childName, views)));
            emitHelpers(clb, thisClass, props);
            if (views == ViewStrategy.MASK) {
                emitComputeViewMask(clb, props, stockName, includeByDefault);
            }
        });
    }

    private static void emitHelpers(java.lang.classfile.ClassBuilder clb, ClassDesc thisClass,
            List<GenWProp> props) {
        boolean needName = false;
        boolean[] kinds = new boolean[WKind.values().length];
        for (GenWProp p : props) {
            kinds[p.kind().ordinal()] = true;
            if (p.kind() == WKind.CHILD) {
                needName = true;
            }
        }
        if (kinds[WKind.STRING.ordinal()]) {
            emitPadded(clb, "$str", MTD_HELP_STRING, cob -> {
                cob.aload(0).aload(1)
                   .invokevirtual(CD_JSON_GENERATOR, "writeName", MTD_WRITE_NAME).pop();
                Label isNull = cob.newLabel();
                Label done = cob.newLabel();
                cob.aload(2).ifnull(isNull);
                cob.aload(0).aload(2)
                   .invokevirtual(CD_JSON_GENERATOR, "writeString", MTD_WRITE_STRING).pop();
                cob.goto_(done);
                cob.labelBinding(isNull);
                cob.aload(0).invokevirtual(CD_JSON_GENERATOR, "writeNull", MTD_WRITE_NULL).pop();
                cob.labelBinding(done);
                cob.return_();
            });
        }
        if (kinds[WKind.INT.ordinal()]) {
            emitPadded(clb, "$int", MTD_HELP_INT, cob -> cob.aload(0).aload(1)
                    .invokevirtual(CD_JSON_GENERATOR, "writeName", MTD_WRITE_NAME).pop()
                    .aload(0).iload(2)
                    .invokevirtual(CD_JSON_GENERATOR, "writeNumber", MTD_WRITE_INT).pop()
                    .return_());
        }
        if (kinds[WKind.LONG.ordinal()]) {
            emitPadded(clb, "$long", MTD_HELP_LONG, cob -> cob.aload(0).aload(1)
                    .invokevirtual(CD_JSON_GENERATOR, "writeName", MTD_WRITE_NAME).pop()
                    .aload(0).lload(2)
                    .invokevirtual(CD_JSON_GENERATOR, "writeNumber", MTD_WRITE_LONG).pop()
                    .return_());
        }
        if (kinds[WKind.BOOLEAN.ordinal()]) {
            emitPadded(clb, "$bool", MTD_HELP_BOOLEAN, cob -> cob.aload(0).aload(1)
                    .invokevirtual(CD_JSON_GENERATOR, "writeName", MTD_WRITE_NAME).pop()
                    .aload(0).iload(2)
                    .invokevirtual(CD_JSON_GENERATOR, "writeBoolean", MTD_WRITE_BOOLEAN).pop()
                    .return_());
        }
        if (needName) {
            emitPadded(clb, "$name", MTD_HELP_NAME, cob -> cob.aload(0).aload(1)
                    .invokevirtual(CD_JSON_GENERATOR, "writeName", MTD_WRITE_NAME).pop()
                    .return_());
        }
    }

    private static void emitPadded(java.lang.classfile.ClassBuilder clb, String name,
            MethodTypeDesc type, java.util.function.Consumer<CodeBuilder> body) {
        MethodParametersAttribute helperParams = (type.parameterCount() == 2)
                ? BeanReaderGenerator.params("g", "name")
                : BeanReaderGenerator.params("g", "name", "value");
        clb.withMethod(name, type, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                mb -> mb.with(helperParams).withCode(cob -> {
            for (int i = 0; i < INLINE_PAD; i++) {
                cob.nop();
            }
            body.accept(cob);
        }));
    }

    private static void buildSerialize(CodeBuilder cob, ClassDesc thisClass, Class<?> beanClass,
            List<GenWProp> props, String[] stockName, String[] nameName, String[] childName,
            ViewStrategy views) {
        final int gen = 2;
        final int ctxt = 3;
        final int beanSlot = 4;
        final int refSlot = 5;
        final int maskSlot = 6;

        ClassDesc beanDesc = beanClass.describeConstable().orElseThrow();
        final boolean itf = beanClass.isInterface();

        Label scopeStart = cob.newBoundLabel();
        Label delegate = null;
        if (views == ViewStrategy.DELEGATE) {
            // View-active calls (declared views with more than 64 properties,
            // or the write-nothing inclusion-off case) go to the stock
            // serializer, whose tail sits after the body so the main path
            // decompiles un-nested.
            delegate = cob.newLabel();
            cob.aload(ctxt).invokevirtual(CD_SER_CONTEXT, "getActiveView", MTD_GET_ACTIVE_VIEW);
            cob.ifnonnull(delegate);
        } else if (views == ViewStrategy.MASK) {
            Label nullView = cob.newLabel();
            Label haveMask = cob.newLabel();
            cob.aload(ctxt).invokevirtual(CD_SER_CONTEXT, "getActiveView", MTD_GET_ACTIVE_VIEW);
            cob.dup();
            cob.ifnull(nullView);
            cob.aload(0);
            cob.swap();
            cob.invokevirtual(CD_WRITER_BASE, "_viewMask", MTD_VIEW_MASK);
            cob.lstore(maskSlot);
            cob.goto_(haveMask);
            cob.labelBinding(nullView);
            cob.pop();
            cob.loadConstant(-1L);
            cob.lstore(maskSlot);
            cob.labelBinding(haveMask);
        }

        cob.aload(1).checkcast(beanDesc).astore(beanSlot);
        cob.aload(gen).aload(beanSlot)
           .invokevirtual(CD_JSON_GENERATOR, "writeStartObject", MTD_WRITE_START_OBJECT).pop();

        for (int i = 0; i < props.size(); i++) {
            GenWProp prop = props.get(i);
            Label hidden = null;
            if (views == ViewStrategy.MASK) {
                hidden = cob.newLabel();
                cob.lload(maskSlot);
                cob.loadConstant(1L << i);
                cob.land();
                cob.lconst_0();
                cob.lcmp();
                cob.ifeq(hidden);
            }
            switch (prop.kind()) {
                case INT -> emitScalar(cob, thisClass, gen, beanSlot, beanDesc, itf, prop,
                        nameName[i], "$int", MTD_HELP_INT, ConstantDescs.CD_int);
                case LONG -> emitScalar(cob, thisClass, gen, beanSlot, beanDesc, itf, prop,
                        nameName[i], "$long", MTD_HELP_LONG, ConstantDescs.CD_long);
                case BOOLEAN -> emitScalar(cob, thisClass, gen, beanSlot, beanDesc, itf, prop,
                        nameName[i], "$bool", MTD_HELP_BOOLEAN, ConstantDescs.CD_boolean);
                case STRING -> emitScalar(cob, thisClass, gen, beanSlot, beanDesc, itf, prop,
                        nameName[i], "$str", MTD_HELP_STRING, ConstantDescs.CD_String);
                case CHILD -> {
                    Class<?> childRaw = prop.field() != null
                            ? prop.field().getType() : prop.getter().getReturnType();
                    ClassDesc childType = childRaw.describeConstable().orElseThrow();
                    cob.aload(gen);
                    ldcData(cob, nameName[i], CD_SERIALIZABLE_STRING);
                    cob.invokestatic(thisClass, "$name", MTD_HELP_NAME);
                    cob.aload(beanSlot);
                    emitLoad(cob, itf, beanDesc, prop, childType);
                    cob.astore(refSlot);
                    final String childEntry = childName[i];
                    emitNullableRef(cob, gen, refSlot, () -> {
                        ldcData(cob, childEntry, CD_WRITER_BASE);
                        cob.aload(refSlot).aload(gen).aload(ctxt);
                        cob.invokevirtual(CD_WRITER_BASE, "serialize", MTD_SERIALIZE);
                    });
                }
                case STOCK -> {
                    ldcData(cob, stockName[i], CD_PROPERTY_WRITER);
                    cob.aload(beanSlot).aload(gen).aload(ctxt);
                    cob.invokevirtual(CD_PROPERTY_WRITER, "serializeAsProperty",
                            MTD_SERIALIZE_AS_PROPERTY);
                }
            }
            if (hidden != null) {
                cob.labelBinding(hidden);
            }
        }

        cob.aload(gen).invokevirtual(CD_JSON_GENERATOR, "writeEndObject", MTD_WRITE_END).pop();
        cob.return_();
        if (delegate != null) {
            cob.labelBinding(delegate);
            cob.aload(0).getfield(CD_WRITER_BASE, "_fallback", CD_BEAN_SER_BASE);
            cob.aload(1).aload(gen).aload(ctxt);
            cob.invokevirtual(CD_BEAN_SER_BASE, "serialize", MTD_SERIALIZE);
            cob.return_();
        }

        Label scopeEnd = cob.newBoundLabel();
        cob.localVariable(1, "value", ConstantDescs.CD_Object, scopeStart, scopeEnd);
        cob.localVariable(gen, "g", CD_JSON_GENERATOR, scopeStart, scopeEnd);
        cob.localVariable(ctxt, "ctxt", CD_SER_CONTEXT, scopeStart, scopeEnd);
        cob.localVariable(beanSlot, "bean", beanDesc, scopeStart, scopeEnd);
        // Slot entries must stay within max_locals, so name the child slot
        // only when some CHILD arm actually stores it.
        if (props.stream().anyMatch(pr -> pr.kind() == WKind.CHILD)) {
            cob.localVariable(refSlot, "child", ConstantDescs.CD_Object, scopeStart, scopeEnd);
        }
        if (views == ViewStrategy.MASK) {
            cob.localVariable(maskSlot, "viewMask", ConstantDescs.CD_long, scopeStart, scopeEnd);
        }
    }

    // Overrides GeneratedWriterBase._computeViewMask: bit i set when property
    // i is visible in the view, per the same rule the stock factory uses to
    // build the filtered writer array.
    private static void emitComputeViewMask(java.lang.classfile.ClassBuilder clb,
            List<GenWProp> props, String[] stockName, boolean includeByDefault) {
        clb.withMethod("_computeViewMask", MTD_VIEW_MASK, ClassFile.ACC_PROTECTED,
                mb -> mb.with(BeanReaderGenerator.params("activeView")).withCode(cob -> {
            final int maskSlot = 2;
            Label scopeStart = cob.newBoundLabel();
            cob.lconst_0().lstore(maskSlot);
            for (int i = 0; i < props.size(); i++) {
                Label skip = cob.newLabel();
                ldcData(cob, stockName[i], CD_PROPERTY_WRITER);
                cob.aload(1);
                cob.loadConstant(includeByDefault ? 1 : 0);
                cob.invokestatic(CD_WRITER_BASE, "_propVisible", MTD_PROP_VISIBLE);
                cob.ifeq(skip);
                cob.lload(maskSlot);
                cob.loadConstant(1L << i);
                cob.lor();
                cob.lstore(maskSlot);
                cob.labelBinding(skip);
            }
            cob.lload(maskSlot).lreturn();
            Label scopeEnd = cob.newBoundLabel();
            cob.localVariable(1, "activeView", ConstantDescs.CD_Class, scopeStart, scopeEnd);
            cob.localVariable(maskSlot, "mask", ConstantDescs.CD_long, scopeStart, scopeEnd);
        }));
    }

    private static void emitScalar(CodeBuilder cob, ClassDesc thisClass, int gen, int beanSlot,
            ClassDesc beanDesc, boolean itf, GenWProp prop, String nameEntry, String helper,
            MethodTypeDesc helperType, ClassDesc valDesc) {
        cob.aload(gen);
        ldcData(cob, nameEntry, CD_SERIALIZABLE_STRING);
        cob.aload(beanSlot);
        emitLoad(cob, itf, beanDesc, prop, valDesc);
        cob.invokestatic(thisClass, helper, helperType);
    }

    // Loads the property value from the bean already on the stack: a getfield
    // for a public field, otherwise the getter call.
    private static void emitLoad(CodeBuilder cob, boolean itf, ClassDesc beanDesc,
            GenWProp prop, ClassDesc valDesc) {
        if (prop.field() != null) {
            ClassDesc owner = prop.field().getDeclaringClass().describeConstable().orElseThrow();
            cob.getfield(owner, prop.field().getName(), valDesc);
        } else if (itf) {
            cob.invokeinterface(beanDesc, prop.getter().getName(), MethodTypeDesc.of(valDesc));
        } else {
            cob.invokevirtual(beanDesc, prop.getter().getName(), MethodTypeDesc.of(valDesc));
        }
    }

    private static void emitNullableRef(CodeBuilder cob, int gen, int slot, Runnable nonNull) {
        Label isNull = cob.newLabel();
        Label done = cob.newLabel();
        cob.aload(slot).ifnull(isNull);
        nonNull.run();
        cob.goto_(done);
        cob.labelBinding(isNull);
        cob.aload(gen).invokevirtual(CD_JSON_GENERATOR, "writeNull", MTD_WRITE_NULL).pop();
        cob.labelBinding(done);
    }
}
