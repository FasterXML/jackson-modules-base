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

    public record GenWProp(WKind kind, Method getter, PropertyWriter stock,
            SerializableString name, GeneratedWriterBase child) {}

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
            BeanSerializerBase fallback) throws ReflectiveOperationException {
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

        byte[] bytes = buildClass(beanClass, props, stockIndex, nameIndex, childIndex);
        MethodHandles.Lookup hidden = MethodHandles.lookup().defineHiddenClassWithClassData(
                bytes, List.copyOf(classData), true);
        MethodHandle ctor = hidden.findConstructor(hidden.lookupClass(),
                MethodType.methodType(void.class, BeanSerializerBase.class));
        try {
            return (ValueSerializer<Object>) ctor.invoke(fallback);
        } catch (Throwable t) {
            throw new IllegalStateException("cannot instantiate generated writer", t);
        }
    }

    private static byte[] buildClass(Class<?> beanClass, List<GenWProp> props,
            int[] stockIndex, int[] nameIndex, int[] childIndex) {
        ClassDesc thisClass = ClassDesc.of(
                "tools.jackson.module.blackbird.codegen.BBWriter_" + beanClass.getSimpleName());
        return ClassFile.of().build(thisClass, clb -> {
            clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            clb.withSuperclass(CD_WRITER_BASE);
            clb.withMethodBody(ConstantDescs.INIT_NAME, MTD_CTOR, ClassFile.ACC_PUBLIC,
                    cob -> cob.aload(0).aload(1)
                            .invokespecial(CD_WRITER_BASE, ConstantDescs.INIT_NAME, MTD_CTOR)
                            .return_());
            clb.withMethodBody("serialize", MTD_SERIALIZE, ClassFile.ACC_PUBLIC,
                    cob -> buildSerialize(cob, beanClass, props, stockIndex, nameIndex, childIndex));
        });
    }

    private static void buildSerialize(CodeBuilder cob, Class<?> beanClass, List<GenWProp> props,
            int[] stockIndex, int[] nameIndex, int[] childIndex) {
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
                case INT -> emitPrimitive(cob, gen, beanSlot, beanDesc, itf, prop, nameIndex[i],
                        "writeNumber", MTD_WRITE_INT, ConstantDescs.CD_int);
                case LONG -> emitPrimitive(cob, gen, beanSlot, beanDesc, itf, prop, nameIndex[i],
                        "writeNumber", MTD_WRITE_LONG, ConstantDescs.CD_long);
                case BOOLEAN -> emitPrimitive(cob, gen, beanSlot, beanDesc, itf, prop, nameIndex[i],
                        "writeBoolean", MTD_WRITE_BOOLEAN, ConstantDescs.CD_boolean);
                case STRING -> {
                    emitName(cob, gen, nameIndex[i]);
                    cob.aload(beanSlot);
                    emitGetter(cob, itf, beanDesc, prop.getter().getName(),
                            MethodTypeDesc.of(ConstantDescs.CD_String));
                    cob.astore(refSlot);
                    emitNullableRef(cob, gen, refSlot, () ->
                            cob.aload(gen).aload(refSlot)
                               .invokevirtual(CD_JSON_GENERATOR, "writeString", MTD_WRITE_STRING).pop());
                }
                case CHILD -> {
                    ClassDesc childType = prop.getter().getReturnType()
                            .describeConstable().orElseThrow();
                    emitName(cob, gen, nameIndex[i]);
                    cob.aload(beanSlot);
                    emitGetter(cob, itf, beanDesc, prop.getter().getName(),
                            MethodTypeDesc.of(childType));
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

    private static void emitPrimitive(CodeBuilder cob, int gen, int beanSlot, ClassDesc beanDesc,
            boolean itf, GenWProp prop, int nameIdx, String writer, MethodTypeDesc writerType,
            ClassDesc valDesc) {
        emitName(cob, gen, nameIdx);
        cob.aload(gen);
        cob.aload(beanSlot);
        emitGetter(cob, itf, beanDesc, prop.getter().getName(), MethodTypeDesc.of(valDesc));
        cob.invokevirtual(CD_JSON_GENERATOR, writer, writerType).pop();
    }

    private static void emitGetter(CodeBuilder cob, boolean itf, ClassDesc beanDesc,
            String name, MethodTypeDesc type) {
        if (itf) {
            cob.invokeinterface(beanDesc, name, type);
        } else {
            cob.invokevirtual(beanDesc, name, type);
        }
    }

    private static void emitName(CodeBuilder cob, int gen, int nameIdx) {
        cob.aload(gen);
        cob.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT,
                ConstantDescs.DEFAULT_NAME, CD_SERIALIZABLE_STRING, nameIdx));
        cob.invokevirtual(CD_JSON_GENERATOR, "writeName", MTD_WRITE_NAME).pop();
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
