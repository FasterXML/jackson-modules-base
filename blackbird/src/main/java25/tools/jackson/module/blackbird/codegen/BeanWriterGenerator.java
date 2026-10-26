package tools.jackson.module.blackbird.codegen;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.attribute.MethodParametersAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonToken;
import tools.jackson.core.SerializableString;
import tools.jackson.core.type.WritableTypeId;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.PropertyWriter;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.module.blackbird.codegen.ReaderCodegen.ViewStrategy;
import tools.jackson.module.blackbird.codegen.WriterCodegen.GenWProp;
import tools.jackson.module.blackbird.codegen.WriterCodegen.WKind;
import tools.jackson.module.blackbird.internal.ClassDataBootstrap;
import tools.jackson.module.blackbird.internal.GeneratedWriterBase;

/**
 * Emits the class file of a hidden-class serializer for one bean (see
 * {@link WriterCodegen}, which defines it): a straight-line sequence of
 * writeName/write-value pairs with pre-encoded name constants for eligible
 * properties and the stock PropertyWriter (a named classData constant) for
 * everything else. Beans that declare views resolve a per-view visibility
 * bitmask and each property tests its bit, so view-active writes stay on the
 * generated path; beans with more than 64 properties delegate view-active
 * calls to the stock serializer instead. Compiled at release 25 into
 * META-INF/versions/25, since it uses the ClassFile API; reached through
 * {@link ClassFileEmitters}.
 */
final class BeanWriterGenerator
{
    private static final ClassDesc CD_JSON_GENERATOR = Descs.of(JsonGenerator.class);
    private static final ClassDesc CD_SER_CONTEXT = Descs.of(SerializationContext.class);
    private static final ClassDesc CD_SERIALIZABLE_STRING = Descs.of(SerializableString.class);
    private static final ClassDesc CD_PROPERTY_WRITER = Descs.of(PropertyWriter.class);
    private static final ClassDesc CD_WRITER_BASE =
            Descs.of(GeneratedWriterBase.class);
    private static final ClassDesc CD_BEAN_SER_BASE = Descs.of(BeanSerializerBase.class);
    private static final ClassDesc CD_WRITE_HELPERS = ClassDesc.of(WriterCodegen.HELPERS_CLASS);

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
    private static final MethodTypeDesc MTD_CTOR = MethodTypeDesc.of(ConstantDescs.CD_void,
            CD_BEAN_SER_BASE, ConstantDescs.CD_String);
    private static final MethodTypeDesc MTD_PROP_FAILURE = MethodTypeDesc.of(
            Descs.of(RuntimeException.class), ConstantDescs.CD_Throwable,
            ConstantDescs.CD_Object, ConstantDescs.CD_int, CD_JSON_GENERATOR, CD_SER_CONTEXT);
    private static final ClassDesc CD_STACK_OVERFLOW = Descs.of(StackOverflowError.class);
    private static final MethodTypeDesc MTD_GET_ACTIVE_VIEW =
            MethodTypeDesc.of(ConstantDescs.CD_Class);
    private static final MethodTypeDesc MTD_VIEW_MASK =
            MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_Class);
    private static final MethodTypeDesc MTD_PROP_VISIBLE = MethodTypeDesc.of(
            ConstantDescs.CD_boolean, CD_PROPERTY_WRITER, ConstantDescs.CD_Class,
            ConstantDescs.CD_boolean);
    private static final ClassDesc CD_JSON_TOKEN = Descs.of(JsonToken.class);
    private static final ClassDesc CD_TYPE_SERIALIZER =
            Descs.of(TypeSerializer.class);
    private static final ClassDesc CD_WRITABLE_TYPE_ID =
            Descs.of(WritableTypeId.class);
    private static final MethodTypeDesc MTD_TYPE_ID = MethodTypeDesc.of(
            CD_WRITABLE_TYPE_ID, ConstantDescs.CD_Object, CD_JSON_TOKEN);
    private static final MethodTypeDesc MTD_WRITE_TYPE_PART = MethodTypeDesc.of(
            CD_WRITABLE_TYPE_ID, CD_JSON_GENERATOR, CD_SER_CONTEXT, CD_WRITABLE_TYPE_ID);
    private static final MethodTypeDesc MTD_ASSIGN_CURRENT =
            MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object);
    private static final MethodTypeDesc MTD_SERIALIZE_WITH_TYPE = MethodTypeDesc.of(
            ConstantDescs.CD_void, ConstantDescs.CD_Object, CD_JSON_GENERATOR, CD_SER_CONTEXT,
            CD_TYPE_SERIALIZER);

    // Named class-data entries through the shared classDataEntry bootstrap;
    // rationale in BeanReaderGenerator.
    private static final java.lang.constant.DirectMethodHandleDesc BSM_DATA_ENTRY =
            ConstantDescs.ofConstantBootstrap(Descs.of(ClassDataBootstrap.class),
                    "classDataEntry", ConstantDescs.CD_Object);

    private BeanWriterGenerator() {}

    private static void ldcData(CodeBuilder cob, String name, ClassDesc type) {
        cob.ldc(DynamicConstantDesc.ofNamed(BSM_DATA_ENTRY, name, type));
    }

    static byte[] emit(WriterCodegen.Plan plan) {
        return BeanReaderGenerator.withinSizeLimit(buildClass(plan.className(), plan.props(),
                plan.stockName(), plan.nameName(), plan.childName(), plan.handleName(),
                plan.views(), plan.includeByDefault(), plan.nativePolyWrites()));
    }

    // HotSpot C2 measure. The nops make each write helper larger than
    // FreqInlineSize (325 bytes, C2) and C1MaxInlineSize (35 bytes, C1), so
    // neither compiler inlines it into serialize() and it compiles once as
    // its own unit. On aarch64, C2 copies of writeName and the NumberOutput
    // digit code inlined into writers that hold list loops spill loop state
    // to the stack. All writers share one WriteHelpers class: with a copy per
    // writer class, each copy compiles separately, and on x86_64 the extra
    // machine code causes instruction-cache misses. With the padding,
    // RecordBenchmark.recordSer is 24-45% faster and PojoBenchmark.mediaItemSer
    // 12-31% faster on aarch64 (JDK 25 and 27-ea), and
    // BuilderBenchmark.builderSer 23% and mediaItemSer 6% faster on x86_64
    // (JDK 25). On x86_64, shared helpers make recordSer 12% and mediaItemSer
    // 7% faster than a copy per writer class. Graal uses other inlining rules,
    // and the interpreter executes the nops until tier-up.
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

    private static byte[] buildClass(String className, List<GenWProp> props,
            String[] stockName, String[] nameName, String[] childName, String[] handleName,
            ViewStrategy views, boolean includeByDefault, boolean nativePolyWrites) {
        ClassDesc thisClass = ClassDesc.of(className);
        return BeanReaderGenerator.CLASS_FILE.build(thisClass, clb -> {
            clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            clb.withSuperclass(CD_WRITER_BASE);
            clb.withMethod(ConstantDescs.INIT_NAME, MTD_CTOR, ClassFile.ACC_PUBLIC,
                    mb -> mb.with(BeanReaderGenerator.params("fallback", "layout"))
                            .withCode(cob -> cob.aload(0).aload(1).aload(2)
                                    .invokespecial(CD_WRITER_BASE, ConstantDescs.INIT_NAME, MTD_CTOR)
                                    .return_()));
            clb.withMethod("serialize", MTD_SERIALIZE, ClassFile.ACC_PUBLIC,
                    mb -> mb.with(BeanReaderGenerator.params("value", "g", "ctxt"))
                            .withCode(cob -> buildSerialize(cob, props,
                                    stockName, nameName, childName, handleName, views)));
            if (nativePolyWrites) {
                clb.withMethod("serializeWithType", MTD_SERIALIZE_WITH_TYPE, ClassFile.ACC_PUBLIC,
                        mb -> mb.with(BeanReaderGenerator.params("value", "g", "ctxt", "typeSer"))
                                .withCode(cob -> buildSerializeWithType(cob, props,
                                        stockName, nameName, childName, handleName, views)));
            }
            if (views == ViewStrategy.MASK) {
                emitComputeViewMask(clb, props, stockName, includeByDefault);
            }
        });
    }

    /**
     * Emits {@link WriterCodegen#HELPERS_CLASS}, the padded write helpers
     * that every generated writer calls (see {@link #INLINE_PAD}).
     */
    static byte[] emitWriteHelpers() {
        return BeanReaderGenerator.CLASS_FILE.build(CD_WRITE_HELPERS, clb -> {
            clb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
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
            emitPadded(clb, "$int", MTD_HELP_INT, cob -> cob.aload(0).aload(1)
                    .invokevirtual(CD_JSON_GENERATOR, "writeName", MTD_WRITE_NAME).pop()
                    .aload(0).iload(2)
                    .invokevirtual(CD_JSON_GENERATOR, "writeNumber", MTD_WRITE_INT).pop()
                    .return_());
            emitPadded(clb, "$long", MTD_HELP_LONG, cob -> cob.aload(0).aload(1)
                    .invokevirtual(CD_JSON_GENERATOR, "writeName", MTD_WRITE_NAME).pop()
                    .aload(0).lload(2)
                    .invokevirtual(CD_JSON_GENERATOR, "writeNumber", MTD_WRITE_LONG).pop()
                    .return_());
            emitPadded(clb, "$bool", MTD_HELP_BOOLEAN, cob -> cob.aload(0).aload(1)
                    .invokevirtual(CD_JSON_GENERATOR, "writeName", MTD_WRITE_NAME).pop()
                    .aload(0).iload(2)
                    .invokevirtual(CD_JSON_GENERATOR, "writeBoolean", MTD_WRITE_BOOLEAN).pop()
                    .return_());
            emitPadded(clb, "$name", MTD_HELP_NAME, cob -> cob.aload(0).aload(1)
                    .invokevirtual(CD_JSON_GENERATOR, "writeName", MTD_WRITE_NAME).pop()
                    .return_());
        });
    }

    private static void emitPadded(java.lang.classfile.ClassBuilder clb, String name,
            MethodTypeDesc type, java.util.function.Consumer<CodeBuilder> body) {
        MethodParametersAttribute helperParams = (type.parameterCount() == 2)
                ? BeanReaderGenerator.params("g", "name")
                : BeanReaderGenerator.params("g", "name", "value");
        clb.withMethod(name, type, ClassFile.ACC_STATIC,
                mb -> mb.with(helperParams).withCode(cob -> {
            for (int i = 0; i < INLINE_PAD; i++) {
                cob.nop();
            }
            body.accept(cob);
        }));
    }

    private static void buildSerialize(CodeBuilder cob,
            List<GenWProp> props, String[] stockName, String[] nameName, String[] childName,
            String[] handleName, ViewStrategy views) {
        final int gen = 2;
        final int ctxt = 3;
        final int refSlot = 5;
        final int maskSlot = 6;
        final int ixSlot = 8;
        final int excSlot = 9;

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
            emitViewMask(cob, ctxt, maskSlot);
        }

        cob.aload(gen).aload(1)
           .invokevirtual(CD_JSON_GENERATOR, "writeStartObject", MTD_WRITE_START_OBJECT).pop();

        Label handler = emitPropsWithHandler(cob, props, stockName, nameName,
                childName, handleName, views, gen, ctxt, refSlot, maskSlot, ixSlot);

        cob.aload(gen).invokevirtual(CD_JSON_GENERATOR, "writeEndObject", MTD_WRITE_END).pop();
        cob.return_();
        emitPropertyHandler(cob, handler, gen, ctxt, ixSlot, excSlot);
        if (delegate != null) {
            cob.labelBinding(delegate);
            cob.aload(0).aload(1).aload(gen).aload(ctxt);
            cob.invokevirtual(CD_WRITER_BASE, "_stockSerialize", MTD_SERIALIZE);
            cob.return_();
        }

        Label scopeEnd = cob.newBoundLabel();
        cob.localVariable(1, "value", ConstantDescs.CD_Object, scopeStart, scopeEnd);
        cob.localVariable(gen, "g", CD_JSON_GENERATOR, scopeStart, scopeEnd);
        cob.localVariable(ctxt, "ctxt", CD_SER_CONTEXT, scopeStart, scopeEnd);
        // Slot entries must stay within max_locals, so name the child slot
        // only when some CHILD arm actually stores it.
        if (props.stream().anyMatch(pr -> pr.kind() == WKind.CHILD)) {
            cob.localVariable(refSlot, "child", ConstantDescs.CD_Object, scopeStart, scopeEnd);
        }
        cob.localVariable(ixSlot, "propIndex", ConstantDescs.CD_int, scopeStart, scopeEnd);
        cob.localVariable(excSlot, "e", ConstantDescs.CD_Throwable, scopeStart, scopeEnd);
        if (views == ViewStrategy.MASK) {
            cob.localVariable(maskSlot, "viewMask", ConstantDescs.CD_long, scopeStart, scopeEnd);
        }
    }

    // The serializeWithType override: stock BeanSerializerBase's flow with
    // the object-id and filter-id branches omitted (both gate at the factory)
    // and the type id always typeSer.typeId(value, START_OBJECT) - beans with
    // a @JsonTypeId property keep the base forwarding instead. The props body
    // is emitted again rather than shared through a method so the plain
    // serialize path keeps its measured shape.
    private static void buildSerializeWithType(CodeBuilder cob,
            List<GenWProp> props, String[] stockName, String[] nameName, String[] childName,
            String[] handleName, ViewStrategy views) {
        final int gen = 2;
        final int ctxt = 3;
        final int typeSer = 4;
        final int typeIdSlot = 5;
        final int refSlot = 6;
        final int maskSlot = 7;
        final int ixSlot = 9;
        final int excSlot = 10;

        Label scopeStart = cob.newBoundLabel();
        Label delegate = null;
        if (views == ViewStrategy.DELEGATE) {
            delegate = cob.newLabel();
            cob.aload(ctxt).invokevirtual(CD_SER_CONTEXT, "getActiveView", MTD_GET_ACTIVE_VIEW);
            cob.ifnonnull(delegate);
        } else if (views == ViewStrategy.MASK) {
            emitViewMask(cob, ctxt, maskSlot);
        }

        cob.aload(typeSer).aload(1);
        cob.getstatic(CD_JSON_TOKEN, "START_OBJECT", CD_JSON_TOKEN);
        cob.invokevirtual(CD_TYPE_SERIALIZER, "typeId", MTD_TYPE_ID);
        cob.astore(typeIdSlot);
        cob.aload(typeSer).aload(gen).aload(ctxt).aload(typeIdSlot);
        cob.invokevirtual(CD_TYPE_SERIALIZER, "writeTypePrefix", MTD_WRITE_TYPE_PART).pop();
        cob.aload(gen).aload(1)
           .invokevirtual(CD_JSON_GENERATOR, "assignCurrentValue", MTD_ASSIGN_CURRENT);

        Label handler = emitPropsWithHandler(cob, props, stockName, nameName,
                childName, handleName, views, gen, ctxt, refSlot, maskSlot, ixSlot);

        cob.aload(typeSer).aload(gen).aload(ctxt).aload(typeIdSlot);
        cob.invokevirtual(CD_TYPE_SERIALIZER, "writeTypeSuffix", MTD_WRITE_TYPE_PART).pop();
        cob.return_();
        emitPropertyHandler(cob, handler, gen, ctxt, ixSlot, excSlot);
        if (delegate != null) {
            cob.labelBinding(delegate);
            cob.aload(0).aload(1).aload(gen).aload(ctxt).aload(typeSer);
            cob.invokevirtual(CD_WRITER_BASE, "_stockSerializeWithType", MTD_SERIALIZE_WITH_TYPE);
            cob.return_();
        }

        Label scopeEnd = cob.newBoundLabel();
        cob.localVariable(1, "value", ConstantDescs.CD_Object, scopeStart, scopeEnd);
        cob.localVariable(gen, "g", CD_JSON_GENERATOR, scopeStart, scopeEnd);
        cob.localVariable(ctxt, "ctxt", CD_SER_CONTEXT, scopeStart, scopeEnd);
        cob.localVariable(typeSer, "typeSer", CD_TYPE_SERIALIZER, scopeStart, scopeEnd);
        cob.localVariable(typeIdSlot, "typeIdDef", CD_WRITABLE_TYPE_ID, scopeStart, scopeEnd);
        if (props.stream().anyMatch(pr -> pr.kind() == WKind.CHILD)) {
            cob.localVariable(refSlot, "child", ConstantDescs.CD_Object, scopeStart, scopeEnd);
        }
        cob.localVariable(ixSlot, "propIndex", ConstantDescs.CD_int, scopeStart, scopeEnd);
        cob.localVariable(excSlot, "e", ConstantDescs.CD_Throwable, scopeStart, scopeEnd);
        if (views == ViewStrategy.MASK) {
            cob.localVariable(maskSlot, "viewMask", ConstantDescs.CD_long, scopeStart, scopeEnd);
        }
    }

    // MASK strategy: resolves the visibility bitmask for the call (all-ones
    // when no view is active) into maskSlot.
    private static void emitViewMask(CodeBuilder cob, int ctxt, int maskSlot) {
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

    // The property section of stock _serializePropertiesNoView: an Exception
    // goes through wrapAndThrow and a StackOverflowError becomes the
    // "Infinite recursion" DatabindException, both with the reference of the
    // property being written (ixSlot). Returns the handler label, which the
    // caller binds after its return (see emitPropertyHandler).
    private static Label emitPropsWithHandler(CodeBuilder cob,
            List<GenWProp> props, String[] stockName, String[] nameName, String[] childName,
            String[] handleName, ViewStrategy views, int gen, int ctxt, int refSlot,
            int maskSlot, int ixSlot) {
        Label start = cob.newLabel();
        Label end = cob.newLabel();
        Label handler = cob.newLabel();
        cob.exceptionCatch(start, end, handler, ConstantDescs.CD_Exception);
        cob.exceptionCatch(start, end, handler, CD_STACK_OVERFLOW);
        // Assigned before the region so the handler can read it from any
        // instruction in it.
        cob.iconst_0().istore(ixSlot);
        cob.labelBinding(start);
        emitProps(cob, props, stockName, nameName, childName, handleName, views,
                gen, ctxt, refSlot, maskSlot, ixSlot);
        cob.labelBinding(end);
        return handler;
    }

    private static void emitPropertyHandler(CodeBuilder cob, Label handler, int gen, int ctxt,
            int ixSlot, int excSlot) {
        cob.labelBinding(handler);
        cob.astore(excSlot);
        cob.aload(0).aload(excSlot).aload(1).iload(ixSlot).aload(gen).aload(ctxt);
        cob.invokevirtual(CD_WRITER_BASE, "_propertyFailure", MTD_PROP_FAILURE);
        cob.athrow();
    }

    // One writeName/write-value pair per property, the bean in slot 1.
    private static void emitProps(CodeBuilder cob, List<GenWProp> props,
            String[] stockName, String[] nameName, String[] childName, String[] handleName,
            ViewStrategy views, int gen, int ctxt, int refSlot, int maskSlot, int ixSlot) {
        for (int i = 0; i < props.size(); i++) {
            GenWProp prop = props.get(i);
            if (i > 0) {
                cob.loadConstant(i);
                cob.istore(ixSlot);
            }
            Label hidden = null;
            Label next = null;
            if (views == ViewStrategy.MASK) {
                hidden = cob.newLabel();
                next = hasDeclaredViews(prop) ? cob.newLabel() : hidden;
                cob.lload(maskSlot);
                cob.loadConstant(1L << i);
                cob.land();
                cob.lconst_0();
                cob.lcmp();
                cob.ifeq(hidden);
            }
            switch (prop.kind()) {
                case INT -> emitScalar(cob, gen, nameName[i], handleName[i],
                        "$int", MTD_HELP_INT, ConstantDescs.CD_int);
                case LONG -> emitScalar(cob, gen, nameName[i], handleName[i],
                        "$long", MTD_HELP_LONG, ConstantDescs.CD_long);
                case BOOLEAN -> emitScalar(cob, gen, nameName[i], handleName[i],
                        "$bool", MTD_HELP_BOOLEAN, ConstantDescs.CD_boolean);
                case STRING -> {
                    // The getter handle is erased to (Object)Object; the value
                    // is a String at run time (java.lang, not a bean name), so
                    // a checkcast recovers the helper's descriptor.
                    cob.aload(gen);
                    ldcData(cob, nameName[i], CD_SERIALIZABLE_STRING);
                    emitLoad(cob, handleName[i], ConstantDescs.CD_Object);
                    cob.checkcast(ConstantDescs.CD_String);
                    cob.invokestatic(CD_WRITE_HELPERS, "$str", MTD_HELP_STRING);
                }
                case CHILD -> {
                    // A value that is the bean itself goes to the stock
                    // writer, which owns the self-reference handling
                    // (FAIL_ON_SELF_REFERENCES, WRITE_SELF_REFERENCES_AS_NULL)
                    // and reads the value again.
                    Label isNull = cob.newLabel();
                    Label self = cob.newLabel();
                    Label done = cob.newLabel();
                    emitLoad(cob, handleName[i], ConstantDescs.CD_Object);
                    cob.astore(refSlot);
                    cob.aload(refSlot).ifnull(isNull);
                    cob.aload(refSlot).aload(1).if_acmpeq(self);
                    cob.aload(gen);
                    ldcData(cob, nameName[i], CD_SERIALIZABLE_STRING);
                    cob.invokestatic(CD_WRITE_HELPERS, "$name", MTD_HELP_NAME);
                    ldcData(cob, childName[i], CD_WRITER_BASE);
                    cob.aload(refSlot).aload(gen).aload(ctxt);
                    cob.invokevirtual(CD_WRITER_BASE, "serialize", MTD_SERIALIZE);
                    cob.goto_(done);
                    cob.labelBinding(isNull);
                    cob.aload(gen);
                    ldcData(cob, nameName[i], CD_SERIALIZABLE_STRING);
                    cob.invokestatic(CD_WRITE_HELPERS, "$name", MTD_HELP_NAME);
                    cob.aload(gen).invokevirtual(CD_JSON_GENERATOR, "writeNull", MTD_WRITE_NULL).pop();
                    cob.goto_(done);
                    cob.labelBinding(self);
                    ldcData(cob, stockName[i], CD_PROPERTY_WRITER);
                    cob.aload(1).aload(gen).aload(ctxt);
                    cob.invokevirtual(CD_PROPERTY_WRITER, "serializeAsProperty",
                            MTD_SERIALIZE_AS_PROPERTY);
                    cob.labelBinding(done);
                }
                case STOCK -> {
                    ldcData(cob, stockName[i], CD_PROPERTY_WRITER);
                    cob.aload(1).aload(gen).aload(ctxt);
                    cob.invokevirtual(CD_PROPERTY_WRITER, "serializeAsProperty",
                            MTD_SERIALIZE_AS_PROPERTY);
                }
            }
            if (next != hidden) {
                // Hidden by a declared view: stock FilteredBeanPropertyWriter
                // hands it to serializeAsOmittedProperty, which writes a
                // placeholder on generators that cannot omit properties.
                // A view-less property hidden by DEFAULT_VIEW_INCLUSION is
                // left out of stock's filtered array, so writes nothing.
                cob.goto_(next);
                cob.labelBinding(hidden);
                ldcData(cob, stockName[i], CD_PROPERTY_WRITER);
                cob.aload(1).aload(gen).aload(ctxt);
                cob.invokevirtual(CD_PROPERTY_WRITER, "serializeAsOmittedProperty",
                        MTD_SERIALIZE_AS_PROPERTY);
            }
            if (next != null) {
                cob.labelBinding(next);
            }
        }
    }

    private static boolean hasDeclaredViews(GenWProp prop) {
        return prop.stock() instanceof BeanPropertyWriter bpw
                && bpw.getViews() != null && bpw.getViews().length > 0;
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

    private static void emitScalar(CodeBuilder cob, int gen,
            String nameEntry, String handleEntry, String helper,
            MethodTypeDesc helperType, ClassDesc erasedDesc) {
        cob.aload(gen);
        ldcData(cob, nameEntry, CD_SERIALIZABLE_STRING);
        emitLoad(cob, handleEntry, erasedDesc);
        cob.invokestatic(CD_WRITE_HELPERS, helper, helperType);
    }

    // Loads the property value from the bean (parameter 1) through the
    // constant (Object)eV handle.
    private static void emitLoad(CodeBuilder cob, String handleEntry, ClassDesc erasedDesc) {
        ldcData(cob, handleEntry, ConstantDescs.CD_MethodHandle);
        cob.aload(1);
        cob.invokevirtual(ConstantDescs.CD_MethodHandle, "invokeExact",
                MethodTypeDesc.of(erasedDesc, ConstantDescs.CD_Object));
    }
}
