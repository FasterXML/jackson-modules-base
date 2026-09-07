package tools.jackson.module.blackbird.codegen;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.core.SerializableString;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.PropertyWriter;
import tools.jackson.databind.ser.bean.BeanSerializerBase;

/**
 * Emits a hidden-class serializer for one bean: a straight-line sequence of
 * writeName/write-value pairs with pre-encoded name constants for eligible
 * properties and the stock PropertyWriter (a classData constant) for
 * everything else. An active view delegates the whole call to the stock
 * serializer, which owns the filtered-property logic.
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

    private BeanWriterGenerator() {}

    @SuppressWarnings("unchecked")
    public static ValueSerializer<Object> generate(Class<?> beanClass, List<GenWProp> props,
            BeanSerializerBase fallback, MethodHandles.Lookup defineLookup)
            throws ReflectiveOperationException {
        List<Object> classData = new ArrayList<>();
        int[] stockIndex = new int[props.size()];
        int[] nameIndex = new int[props.size()];
        int[] childIndex = new int[props.size()];
        for (int i = 0; i < props.size(); i++) {
            GenWProp p = props.get(i);
            stockIndex[i] = classData.size();
            classData.add(p.stock());
            if (p.name() != null) {
                nameIndex[i] = classData.size();
                classData.add(p.name());
            } else {
                nameIndex[i] = -1;
            }
            if (p.child() != null) {
                childIndex[i] = classData.size();
                classData.add(p.child());
            } else {
                childIndex[i] = -1;
            }
        }

        // Rationale in BeanCodecGenerator: non-public beans define in the
        // bean's package context; a failure there is an environment gate.
        MethodHandles.Lookup definer =
                (defineLookup != null) ? defineLookup : MethodHandles.lookup();
        byte[] bytes = buildClass(definer.lookupClass().getPackageName(), beanClass, props,
                stockIndex, nameIndex, childIndex);
        MethodHandles.Lookup hidden;
        try {
            hidden = definer.defineHiddenClassWithClassData(
                    bytes, List.copyOf(classData), true);
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
            int[] stockIndex, int[] nameIndex, int[] childIndex) {
        // A hidden class must be named in its define context's package.
        ClassDesc thisClass = ClassDesc.of(
                targetPackage + ".BBWriter_" + beanClass.getSimpleName());
        return ClassFile.of().build(thisClass, clb -> {
            clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            clb.withSuperclass(CD_WRITER_BASE);
            clb.withMethodBody(ConstantDescs.INIT_NAME, MTD_CTOR, ClassFile.ACC_PUBLIC,
                    cob -> cob.aload(0).aload(1)
                            .invokespecial(CD_WRITER_BASE, ConstantDescs.INIT_NAME, MTD_CTOR)
                            .return_());
            clb.withMethodBody("serialize", MTD_SERIALIZE, ClassFile.ACC_PUBLIC,
                    cob -> buildSerialize(cob, thisClass, beanClass, props,
                            stockIndex, nameIndex, childIndex));
            emitHelpers(clb, thisClass, props);
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
        clb.withMethodBody(name, type, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, cob -> {
            for (int i = 0; i < INLINE_PAD; i++) {
                cob.nop();
            }
            body.accept(cob);
        });
    }

    private static void buildSerialize(CodeBuilder cob, ClassDesc thisClass, Class<?> beanClass,
            List<GenWProp> props, int[] stockIndex, int[] nameIndex, int[] childIndex) {
        final int gen = 2;
        final int ctxt = 3;
        final int beanSlot = 4;
        final int refSlot = 5;

        ClassDesc beanDesc = beanClass.describeConstable().orElseThrow();
        final boolean itf = beanClass.isInterface();

        Label noView = cob.newLabel();
        cob.aload(ctxt).invokevirtual(CD_SER_CONTEXT, "getActiveView",
                MethodTypeDesc.of(ConstantDescs.CD_Class));
        cob.ifnull(noView);
        cob.aload(0).getfield(CD_WRITER_BASE, "_fallback", CD_BEAN_SER_BASE);
        cob.aload(1).aload(gen).aload(ctxt);
        cob.invokevirtual(CD_BEAN_SER_BASE, "serialize", MTD_SERIALIZE);
        cob.return_();
        cob.labelBinding(noView);

        cob.aload(1).checkcast(beanDesc).astore(beanSlot);
        cob.aload(gen).aload(beanSlot)
           .invokevirtual(CD_JSON_GENERATOR, "writeStartObject", MTD_WRITE_START_OBJECT).pop();

        for (int i = 0; i < props.size(); i++) {
            GenWProp prop = props.get(i);
            switch (prop.kind()) {
                case INT -> emitScalar(cob, thisClass, gen, beanSlot, beanDesc, itf, prop,
                        nameIndex[i], "$int", MTD_HELP_INT, ConstantDescs.CD_int);
                case LONG -> emitScalar(cob, thisClass, gen, beanSlot, beanDesc, itf, prop,
                        nameIndex[i], "$long", MTD_HELP_LONG, ConstantDescs.CD_long);
                case BOOLEAN -> emitScalar(cob, thisClass, gen, beanSlot, beanDesc, itf, prop,
                        nameIndex[i], "$bool", MTD_HELP_BOOLEAN, ConstantDescs.CD_boolean);
                case STRING -> emitScalar(cob, thisClass, gen, beanSlot, beanDesc, itf, prop,
                        nameIndex[i], "$str", MTD_HELP_STRING, ConstantDescs.CD_String);
                case CHILD -> {
                    Class<?> childRaw = prop.field() != null
                            ? prop.field().getType() : prop.getter().getReturnType();
                    ClassDesc childType = childRaw.describeConstable().orElseThrow();
                    cob.aload(gen);
                    cob.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT,
                            ConstantDescs.DEFAULT_NAME, CD_SERIALIZABLE_STRING, nameIndex[i]));
                    cob.invokestatic(thisClass, "$name", MTD_HELP_NAME);
                    cob.aload(beanSlot);
                    emitLoad(cob, itf, beanDesc, prop, childType);
                    cob.astore(refSlot);
                    final int childIdx = childIndex[i];
                    emitNullableRef(cob, gen, refSlot, () -> {
                        cob.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT,
                                ConstantDescs.DEFAULT_NAME, CD_WRITER_BASE, childIdx));
                        cob.aload(refSlot).aload(gen).aload(ctxt);
                        cob.invokevirtual(CD_WRITER_BASE, "serialize", MTD_SERIALIZE);
                    });
                }
                case STOCK -> {
                    cob.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT,
                            ConstantDescs.DEFAULT_NAME, CD_PROPERTY_WRITER, stockIndex[i]));
                    cob.aload(beanSlot).aload(gen).aload(ctxt);
                    cob.invokevirtual(CD_PROPERTY_WRITER, "serializeAsProperty",
                            MTD_SERIALIZE_AS_PROPERTY);
                }
            }
        }

        cob.aload(gen).invokevirtual(CD_JSON_GENERATOR, "writeEndObject", MTD_WRITE_END).pop();
        cob.return_();
    }

    private static void emitScalar(CodeBuilder cob, ClassDesc thisClass, int gen, int beanSlot,
            ClassDesc beanDesc, boolean itf, GenWProp prop, int nameIdx, String helper,
            MethodTypeDesc helperType, ClassDesc valDesc) {
        cob.aload(gen);
        cob.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT,
                ConstantDescs.DEFAULT_NAME, CD_SERIALIZABLE_STRING, nameIdx));
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
