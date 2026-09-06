package tools.jackson.module.blackbird.codegen;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.instruction.SwitchCase;
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

import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.util.ClassUtil;

/**
 * Emits a hidden-class deserializer for one bean: a loop on nextNameMatch, a
 * tableswitch on the property index, inlined scalar reads with direct setter
 * calls for eligible properties, and the stock SettableBeanProperty (a
 * classData constant) for everything else. The matcher and the per-property
 * payloads travel as classData; the fallback deserializer is a constructor
 * argument consumed by {@link GeneratedCodecBase}.
 */
public final class BeanCodecGenerator
{
    public enum Kind { STRING, INT, LONG, BOOLEAN, CHILD, STOCK }

    // setter applies to POJO and builder modes (null when setterHandle carries
    // a non-public setter instead); type is the record component type in
    // record mode and the child value type for CHILD; child is the linked
    // generated codec for CHILD.
    public record GenProp(String name, Kind kind, Method setter, SettableBeanProperty stock,
            Class<?> type, GeneratedCodecBase child, MethodHandle setterHandle) {
        public GenProp(String name, Kind kind, Method setter, SettableBeanProperty stock) {
            this(name, kind, setter, stock, null, null, null);
        }

        public GenProp(String name, Kind kind, Method setter, SettableBeanProperty stock,
                Class<?> type) {
            this(name, kind, setter, stock, type, null, null);
        }
    }

    private static final ClassDesc CD_JSON_PARSER = ClassDesc.of("tools.jackson.core.JsonParser");
    private static final ClassDesc CD_JSON_TOKEN = ClassDesc.of("tools.jackson.core.JsonToken");
    private static final ClassDesc CD_DESER_CONTEXT = ClassDesc.of("tools.jackson.databind.DeserializationContext");
    private static final ClassDesc CD_NAME_MATCHER = ClassDesc.of("tools.jackson.core.sym.PropertyNameMatcher");
    private static final ClassDesc CD_SETTABLE_PROP = ClassDesc.of("tools.jackson.databind.deser.SettableBeanProperty");
    // Derived from the class literal rather than a name: the test build
    // compiles main sources into target/test-classes through --patch-module,
    // and javac emits only compile-time-referenced classes there. The class
    // literal makes sure that GeneratedCodecBase.class is present in the test
    // module, which shadows target/classes at run time. Every same-module
    // class that generated code names only as a string needs such a
    // compile-time reference.
    private static final ClassDesc CD_BASE =
            GeneratedCodecBase.class.describeConstable().orElseThrow();
    private static final ClassDesc CD_BEAN_DESER_BASE = ClassDesc.of("tools.jackson.databind.deser.bean.BeanDeserializerBase");
    private static final ClassDesc CD_ISE = ClassDesc.of("java.lang.IllegalStateException");

    private static final MethodTypeDesc MTD_NEXT_NAME_MATCH =
            MethodTypeDesc.of(ConstantDescs.CD_int, CD_NAME_MATCHER);
    private static final MethodTypeDesc MTD_NEXT_TOKEN = MethodTypeDesc.of(CD_JSON_TOKEN);
    private static final MethodTypeDesc MTD_SKIP_CHILDREN = MethodTypeDesc.of(CD_JSON_PARSER);
    private static final MethodTypeDesc MTD_GET_STRING = MethodTypeDesc.of(ConstantDescs.CD_String);
    private static final MethodTypeDesc MTD_GET_INT = MethodTypeDesc.of(ConstantDescs.CD_int);
    private static final MethodTypeDesc MTD_GET_LONG = MethodTypeDesc.of(ConstantDescs.CD_long);
    private static final MethodTypeDesc MTD_GET_BOOLEAN = MethodTypeDesc.of(ConstantDescs.CD_boolean);
    private static final MethodTypeDesc MTD_ASSIGN_CURRENT =
            MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object);
    private static final MethodTypeDesc MTD_DESERIALIZE_AND_SET = MethodTypeDesc.of(
            ConstantDescs.CD_void, CD_JSON_PARSER, CD_DESER_CONTEXT, ConstantDescs.CD_Object);
    private static final MethodTypeDesc MTD_DESERIALIZE =
            MethodTypeDesc.of(ConstantDescs.CD_Object, CD_JSON_PARSER, CD_DESER_CONTEXT);
    private static final MethodTypeDesc MTD_CTOR =
            MethodTypeDesc.of(ConstantDescs.CD_void, CD_BEAN_DESER_BASE);

    private BeanCodecGenerator() {}

    public static ValueDeserializer<Object> generate(Class<?> beanClass, List<GenProp> props,
            PropertyNameMatcher matcher, BeanDeserializerBase fallback)
            throws ReflectiveOperationException {
        return generate(beanClass, props, matcher, fallback, null);
    }

    // recordCtor non-null selects record mode: props are in canonical
    // constructor order, values collect into typed locals, and the
    // constructor MethodHandle (exact component signature) builds the value.
    public static ValueDeserializer<Object> generate(Class<?> beanClass, List<GenProp> props,
            PropertyNameMatcher matcher, BeanDeserializerBase fallback, MethodHandle recordCtor)
            throws ReflectiveOperationException {
        return generate(beanClass, props, matcher, fallback, recordCtor, null);
    }

    // builderSupport non-null selects builder mode: values apply to a builder
    // instance created by the stock ValueInstantiator, fluent setter returns
    // replace the builder local, and the build MethodHandle (asType'd to
    // (Object)Object) produces the value.
    public record BuilderSupport(tools.jackson.databind.deser.ValueInstantiator instantiator,
            MethodHandle buildMethod, Class<?> builderClass) {}

    @SuppressWarnings("unchecked")
    public static ValueDeserializer<Object> generate(Class<?> beanClass, List<GenProp> props,
            PropertyNameMatcher matcher, BeanDeserializerBase fallback, MethodHandle recordCtor,
            BuilderSupport builder)
            throws ReflectiveOperationException {
        List<Object> classData = new ArrayList<>();
        classData.add(matcher);
        // Tier-A properties also carry their SettableBeanProperty: their
        // VALUE_NULL branch runs through it, since null handling is
        // configuration-dependent per property.
        int[] stockIndex = new int[props.size()];
        int[] childIndex = new int[props.size()];
        int[] setterMhIndex = new int[props.size()];
        for (int i = 0; i < props.size(); i++) {
            GenProp gp = props.get(i);
            stockIndex[i] = classData.size();
            classData.add(gp.stock());
            if (gp.child() != null) {
                childIndex[i] = classData.size();
                classData.add(gp.child());
            } else {
                childIndex[i] = -1;
            }
            if (gp.setterHandle() != null) {
                setterMhIndex[i] = classData.size();
                classData.add(gp.setterHandle());
            } else {
                setterMhIndex[i] = -1;
            }
        }
        int ctorIndex = -1;
        if (recordCtor != null) {
            ctorIndex = classData.size();
            classData.add(recordCtor);
        }
        int instIndex = -1;
        int buildIndex = -1;
        if (builder != null) {
            instIndex = classData.size();
            classData.add(builder.instantiator());
            buildIndex = classData.size();
            classData.add(builder.buildMethod());
        }

        byte[] bytes = buildClass(beanClass, props, stockIndex, childIndex, setterMhIndex,
                ctorIndex, builder == null ? null : builder.builderClass(), instIndex, buildIndex);
        // No ClassOption.STRONG: the codec instance held by the mapper's
        // deserializer cache anchors the class, so codecs unload with the
        // mapper instead of pinning metaspace for the loader's lifetime.
        MethodHandles.Lookup hidden = MethodHandles.lookup().defineHiddenClassWithClassData(
                bytes, List.copyOf(classData), true);
        MethodHandle ctor = hidden.findConstructor(hidden.lookupClass(),
                MethodType.methodType(void.class, BeanDeserializerBase.class));
        try {
            return (ValueDeserializer<Object>) ctor.invoke(fallback);
        } catch (Throwable t) {
            throw new IllegalStateException("cannot instantiate generated codec", t);
        }
    }

    private static byte[] buildClass(Class<?> beanClass, List<GenProp> props,
            int[] stockIndex, int[] childIndex, int[] setterMhIndex,
            int ctorIndex, Class<?> builderClass, int instIndex, int buildIndex) {
        ClassDesc thisClass = ClassDesc.of(
                "tools.jackson.module.blackbird.codegen.BBCodec_" + beanClass.getSimpleName());
        return ClassFile.of().build(thisClass, clb -> {
            clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            clb.withSuperclass(CD_BASE);
            clb.withMethodBody(ConstantDescs.INIT_NAME, MTD_CTOR, ClassFile.ACC_PUBLIC,
                    cob -> cob.aload(0).aload(1)
                            .invokespecial(CD_BASE, ConstantDescs.INIT_NAME, MTD_CTOR)
                            .return_());
            clb.withMethodBody("deserialize", MTD_DESERIALIZE, ClassFile.ACC_PUBLIC,
                    cob -> {
                        if (builderClass != null) {
                            buildBuilderDeserialize(cob, builderClass, props, stockIndex,
                                    childIndex, instIndex, buildIndex);
                        } else if (ctorIndex < 0) {
                            buildDeserialize(cob, beanClass, props, stockIndex, childIndex,
                                    setterMhIndex);
                        } else {
                            buildRecordDeserialize(cob, beanClass, props, stockIndex, childIndex,
                                    ctorIndex);
                        }
                    });
        });
    }

    private static void buildDeserialize(CodeBuilder cob, Class<?> beanClass,
            List<GenProp> props, int[] stockIndex, int[] childIndex, int[] setterMhIndex) {
        final int parser = 1;
        final int ctxt = 2;
        final int beanSlot = 3;
        final int matcherSlot = 4;
        final int ixSlot = 5;

        ClassDesc beanDesc = beanClass.describeConstable().orElseThrow();

        Label noView = cob.newLabel();
        cob.aload(ctxt).invokevirtual(CD_DESER_CONTEXT, "getActiveView",
                MethodTypeDesc.of(ConstantDescs.CD_Class));
        cob.ifnull(noView);
        cob.aload(0).getfield(CD_BASE, "_fallback", CD_BEAN_DESER_BASE);
        cob.aload(parser).aload(ctxt);
        cob.invokevirtual(CD_BEAN_DESER_BASE, "deserialize", MTD_DESERIALIZE);
        cob.areturn();
        cob.labelBinding(noView);

        cob.new_(beanDesc).dup()
           .invokespecial(beanDesc, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
           .astore(beanSlot);
        cob.aload(parser).aload(beanSlot)
           .invokevirtual(CD_JSON_PARSER, "assignCurrentValue", MTD_ASSIGN_CURRENT);

        cob.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT,
                ConstantDescs.DEFAULT_NAME, CD_NAME_MATCHER, 0));
        cob.astore(matcherSlot);

        Label loop = cob.newLabel();
        Label switchPart = cob.newLabel();
        Label endObject = cob.newLabel();
        Label unknown = cob.newLabel();
        Label oddToken = cob.newLabel();
        Label defaultCase = cob.newLabel();

        nextNameMatch(cob, parser, matcherSlot, ixSlot);

        cob.labelBinding(loop);
        cob.iload(ixSlot).ifge(switchPart);
        cob.iload(ixSlot).iconst_m1().if_icmpeq(endObject);
        cob.iload(ixSlot).ldc(-2).if_icmpeq(unknown);
        cob.goto_(oddToken);

        cob.labelBinding(switchPart);
        List<SwitchCase> cases = new ArrayList<>(props.size());
        Label[] caseLabels = new Label[props.size()];
        for (int i = 0; i < props.size(); i++) {
            caseLabels[i] = cob.newLabel();
            cases.add(SwitchCase.of(i, caseLabels[i]));
        }
        cob.iload(ixSlot);
        cob.tableswitch(0, props.size() - 1, defaultCase, cases);

        for (int i = 0; i < props.size(); i++) {
            cob.labelBinding(caseLabels[i]);
            GenProp prop = props.get(i);
            switch (prop.kind()) {
                case STRING -> emitScalar(cob, beanSlot, parser, ctxt, beanDesc, prop,
                        "getString", MTD_GET_STRING, ConstantDescs.CD_String,
                        stockIndex[i], setterMhIndex[i]);
                case INT -> emitScalar(cob, beanSlot, parser, ctxt, beanDesc, prop,
                        "getIntValue", MTD_GET_INT, ConstantDescs.CD_int,
                        stockIndex[i], setterMhIndex[i]);
                case LONG -> emitScalar(cob, beanSlot, parser, ctxt, beanDesc, prop,
                        "getLongValue", MTD_GET_LONG, ConstantDescs.CD_long,
                        stockIndex[i], setterMhIndex[i]);
                case BOOLEAN -> emitScalar(cob, beanSlot, parser, ctxt, beanDesc, prop,
                        "getBooleanValue", MTD_GET_BOOLEAN, ConstantDescs.CD_boolean,
                        stockIndex[i], setterMhIndex[i]);
                case CHILD -> {
                    Label childStock = cob.newLabel();
                    Label childDone = cob.newLabel();
                    ClassDesc childType = prop.type().describeConstable().orElseThrow();
                    cob.aload(parser).invokevirtual(CD_JSON_PARSER, "nextToken", MTD_NEXT_TOKEN);
                    cob.getstatic(CD_JSON_TOKEN, "START_OBJECT", CD_JSON_TOKEN);
                    cob.if_acmpne(childStock);
                    cob.aload(beanSlot);
                    emitChildCall(cob, parser, ctxt, childIndex[i]);
                    cob.checkcast(childType);
                    invokeSetter(cob, beanDesc, prop, childType);
                    cob.goto_(childDone);
                    cob.labelBinding(childStock);
                    emitStockSet(cob, parser, ctxt, beanSlot, stockIndex[i]);
                    cob.labelBinding(childDone);
                }
                case STOCK -> {
                    cob.aload(parser).invokevirtual(CD_JSON_PARSER, "nextToken", MTD_NEXT_TOKEN).pop();
                    emitStockSet(cob, parser, ctxt, beanSlot, stockIndex[i]);
                }
            }
            nextNameMatch(cob, parser, matcherSlot, ixSlot);
            cob.goto_(loop);
        }

        cob.labelBinding(defaultCase);
        throwIse(cob, "bad property index");

        cob.labelBinding(endObject);
        cob.aload(beanSlot).areturn();

        cob.labelBinding(unknown);
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "nextToken", MTD_NEXT_TOKEN).pop();
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "skipChildren", MTD_SKIP_CHILDREN).pop();
        nextNameMatch(cob, parser, matcherSlot, ixSlot);
        cob.goto_(loop);

        cob.labelBinding(oddToken);
        cob.aload(0).aload(parser).aload(ctxt)
           .invokevirtual(CD_BASE, "_unexpectedToken", MTD_DESERIALIZE)
           .areturn();
    }

    private static final MethodTypeDesc MTD_PROP_DESERIALIZE =
            MethodTypeDesc.of(ConstantDescs.CD_Object, CD_JSON_PARSER, CD_DESER_CONTEXT);
    private static final ClassDesc CD_VALUE_INSTANTIATOR =
            ClassDesc.of("tools.jackson.databind.deser.ValueInstantiator");
    private static final MethodTypeDesc MTD_CREATE_DEFAULT =
            MethodTypeDesc.of(ConstantDescs.CD_Object, CD_DESER_CONTEXT);
    private static final MethodTypeDesc MTD_DESER_SET_RETURN = MethodTypeDesc.of(
            ConstantDescs.CD_Object, CD_JSON_PARSER, CD_DESER_CONTEXT, ConstantDescs.CD_Object);
    private static final MethodTypeDesc MTD_BUILD_INVOKE =
            MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_Object);

    private static void buildBuilderDeserialize(CodeBuilder cob, Class<?> builderClass,
            List<GenProp> props, int[] stockIndex, int[] childIndex, int instIndex, int buildIndex) {
        final int parser = 1;
        final int ctxt = 2;
        final int builderSlot = 3;
        final int matcherSlot = 4;
        final int ixSlot = 5;

        ClassDesc builderDesc = builderClass.describeConstable().orElseThrow();

        Label noView = cob.newLabel();
        cob.aload(ctxt).invokevirtual(CD_DESER_CONTEXT, "getActiveView",
                MethodTypeDesc.of(ConstantDescs.CD_Class));
        cob.ifnull(noView);
        cob.aload(0).getfield(CD_BASE, "_fallback", CD_BEAN_DESER_BASE);
        cob.aload(parser).aload(ctxt);
        cob.invokevirtual(CD_BEAN_DESER_BASE, "deserialize", MTD_DESERIALIZE);
        cob.areturn();
        cob.labelBinding(noView);

        cob.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT,
                ConstantDescs.DEFAULT_NAME, CD_VALUE_INSTANTIATOR, instIndex));
        cob.aload(ctxt);
        cob.invokevirtual(CD_VALUE_INSTANTIATOR, "createUsingDefault", MTD_CREATE_DEFAULT);
        cob.checkcast(builderDesc);
        cob.astore(builderSlot);

        cob.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT,
                ConstantDescs.DEFAULT_NAME, CD_NAME_MATCHER, 0));
        cob.astore(matcherSlot);

        Label loop = cob.newLabel();
        Label switchPart = cob.newLabel();
        Label endObject = cob.newLabel();
        Label unknown = cob.newLabel();
        Label oddToken = cob.newLabel();
        Label defaultCase = cob.newLabel();

        nextNameMatch(cob, parser, matcherSlot, ixSlot);

        cob.labelBinding(loop);
        cob.iload(ixSlot).ifge(switchPart);
        cob.iload(ixSlot).iconst_m1().if_icmpeq(endObject);
        cob.iload(ixSlot).ldc(-2).if_icmpeq(unknown);
        cob.goto_(oddToken);

        cob.labelBinding(switchPart);
        List<SwitchCase> cases = new ArrayList<>(props.size());
        Label[] caseLabels = new Label[props.size()];
        for (int i = 0; i < props.size(); i++) {
            caseLabels[i] = cob.newLabel();
            cases.add(SwitchCase.of(i, caseLabels[i]));
        }
        cob.iload(ixSlot);
        cob.tableswitch(0, props.size() - 1, defaultCase, cases);

        for (int i = 0; i < props.size(); i++) {
            cob.labelBinding(caseLabels[i]);
            GenProp prop = props.get(i);
            switch (prop.kind()) {
                case STRING -> emitBuilderScalar(cob, parser, ctxt, builderSlot, builderDesc, prop,
                        "getString", MTD_GET_STRING, ConstantDescs.CD_String, stockIndex[i]);
                case INT -> emitBuilderScalar(cob, parser, ctxt, builderSlot, builderDesc, prop,
                        "getIntValue", MTD_GET_INT, ConstantDescs.CD_int, stockIndex[i]);
                case LONG -> emitBuilderScalar(cob, parser, ctxt, builderSlot, builderDesc, prop,
                        "getLongValue", MTD_GET_LONG, ConstantDescs.CD_long, stockIndex[i]);
                case BOOLEAN -> emitBuilderScalar(cob, parser, ctxt, builderSlot, builderDesc, prop,
                        "getBooleanValue", MTD_GET_BOOLEAN, ConstantDescs.CD_boolean, stockIndex[i]);
                case CHILD -> {
                    Label childStock = cob.newLabel();
                    Label childDone = cob.newLabel();
                    ClassDesc childType = prop.type().describeConstable().orElseThrow();
                    cob.aload(parser).invokevirtual(CD_JSON_PARSER, "nextToken", MTD_NEXT_TOKEN);
                    cob.getstatic(CD_JSON_TOKEN, "START_OBJECT", CD_JSON_TOKEN);
                    cob.if_acmpne(childStock);
                    cob.aload(builderSlot);
                    emitChildCall(cob, parser, ctxt, childIndex[i]);
                    cob.checkcast(childType);
                    Class<?> childRet = prop.setter().getReturnType();
                    MethodTypeDesc childSetter = MethodTypeDesc.of(
                            childRet == void.class ? ConstantDescs.CD_void
                                    : childRet.describeConstable().orElseThrow(),
                            childType);
                    cob.invokevirtual(builderDesc, prop.setter().getName(), childSetter);
                    if (childRet != void.class) {
                        cob.astore(builderSlot);
                    }
                    cob.goto_(childDone);
                    cob.labelBinding(childStock);
                    emitStockSetReturn(cob, parser, ctxt, builderSlot, builderDesc, stockIndex[i]);
                    cob.labelBinding(childDone);
                }
                case STOCK -> {
                    cob.aload(parser).invokevirtual(CD_JSON_PARSER, "nextToken", MTD_NEXT_TOKEN).pop();
                    emitStockSetReturn(cob, parser, ctxt, builderSlot, builderDesc, stockIndex[i]);
                }
            }
            nextNameMatch(cob, parser, matcherSlot, ixSlot);
            cob.goto_(loop);
        }

        cob.labelBinding(defaultCase);
        throwIse(cob, "bad property index");

        cob.labelBinding(endObject);
        cob.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT,
                ConstantDescs.DEFAULT_NAME, ConstantDescs.CD_MethodHandle, buildIndex));
        cob.aload(builderSlot);
        cob.invokevirtual(ConstantDescs.CD_MethodHandle, "invokeExact", MTD_BUILD_INVOKE);
        cob.areturn();

        cob.labelBinding(unknown);
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "nextToken", MTD_NEXT_TOKEN).pop();
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "skipChildren", MTD_SKIP_CHILDREN).pop();
        nextNameMatch(cob, parser, matcherSlot, ixSlot);
        cob.goto_(loop);

        cob.labelBinding(oddToken);
        cob.aload(0).aload(parser).aload(ctxt)
           .invokevirtual(CD_BASE, "_unexpectedToken", MTD_DESERIALIZE)
           .areturn();
    }

    private static void emitBuilderScalar(CodeBuilder cob, int parser, int ctxt, int builderSlot,
            ClassDesc builderDesc, GenProp prop, String getter, MethodTypeDesc getterType,
            ClassDesc valueDesc, int stockIdx) {
        Label isNull = cob.newLabel();
        Label done = cob.newLabel();
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "nextToken", MTD_NEXT_TOKEN);
        cob.getstatic(CD_JSON_TOKEN, "VALUE_NULL", CD_JSON_TOKEN);
        cob.if_acmpeq(isNull);
        cob.aload(builderSlot);
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, getter, getterType);
        Class<?> ret = prop.setter().getReturnType();
        MethodTypeDesc setterType = MethodTypeDesc.of(
                ret == void.class ? ConstantDescs.CD_void : ret.describeConstable().orElseThrow(),
                valueDesc);
        cob.invokevirtual(builderDesc, prop.setter().getName(), setterType);
        if (ret != void.class) {
            cob.astore(builderSlot);
        }
        cob.goto_(done);
        cob.labelBinding(isNull);
        emitStockSetReturn(cob, parser, ctxt, builderSlot, builderDesc, stockIdx);
        cob.labelBinding(done);
    }

    // Builder properties apply through deserializeSetAndReturn: fluent
    // builders may return a replacement instance, which becomes the new
    // builder local.
    private static void emitStockSetReturn(CodeBuilder cob, int parser, int ctxt, int builderSlot,
            ClassDesc builderDesc, int stockIdx) {
        cob.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT,
                ConstantDescs.DEFAULT_NAME, CD_SETTABLE_PROP, stockIdx));
        cob.aload(parser).aload(ctxt).aload(builderSlot);
        cob.invokevirtual(CD_SETTABLE_PROP, "deserializeSetAndReturn", MTD_DESER_SET_RETURN);
        cob.checkcast(builderDesc);
        cob.astore(builderSlot);
    }

    private static void buildRecordDeserialize(CodeBuilder cob, Class<?> beanClass,
            List<GenProp> props, int[] stockIndex, int[] childIndex, int ctorIndex) {
        final int parser = 1;
        final int ctxt = 2;

        int next = 3;
        int[] componentSlot = new int[props.size()];
        for (int i = 0; i < props.size(); i++) {
            componentSlot[i] = next;
            Class<?> t = props.get(i).type();
            next += (t == long.class || t == double.class) ? 2 : 1;
        }
        final int matcherSlot = next++;
        final int ixSlot = next;

        ClassDesc recordDesc = beanClass.describeConstable().orElseThrow();

        Label noView = cob.newLabel();
        cob.aload(ctxt).invokevirtual(CD_DESER_CONTEXT, "getActiveView",
                MethodTypeDesc.of(ConstantDescs.CD_Class));
        cob.ifnull(noView);
        cob.aload(0).getfield(CD_BASE, "_fallback", CD_BEAN_DESER_BASE);
        cob.aload(parser).aload(ctxt);
        cob.invokevirtual(CD_BEAN_DESER_BASE, "deserialize", MTD_DESERIALIZE);
        cob.areturn();
        cob.labelBinding(noView);

        for (int i = 0; i < props.size(); i++) {
            Class<?> t = props.get(i).type();
            if (t == long.class) {
                cob.lconst_0().lstore(componentSlot[i]);
            } else if (t == double.class) {
                cob.dconst_0().dstore(componentSlot[i]);
            } else if (t == float.class) {
                cob.fconst_0().fstore(componentSlot[i]);
            } else if (t.isPrimitive()) {
                cob.iconst_0().istore(componentSlot[i]);
            } else {
                cob.aconst_null().astore(componentSlot[i]);
            }
        }

        cob.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT,
                ConstantDescs.DEFAULT_NAME, CD_NAME_MATCHER, 0));
        cob.astore(matcherSlot);

        Label loop = cob.newLabel();
        Label switchPart = cob.newLabel();
        Label endObject = cob.newLabel();
        Label unknown = cob.newLabel();
        Label oddToken = cob.newLabel();
        Label defaultCase = cob.newLabel();

        nextNameMatch(cob, parser, matcherSlot, ixSlot);

        cob.labelBinding(loop);
        cob.iload(ixSlot).ifge(switchPart);
        cob.iload(ixSlot).iconst_m1().if_icmpeq(endObject);
        cob.iload(ixSlot).ldc(-2).if_icmpeq(unknown);
        cob.goto_(oddToken);

        cob.labelBinding(switchPart);
        List<SwitchCase> cases = new ArrayList<>(props.size());
        Label[] caseLabels = new Label[props.size()];
        for (int i = 0; i < props.size(); i++) {
            caseLabels[i] = cob.newLabel();
            cases.add(SwitchCase.of(i, caseLabels[i]));
        }
        cob.iload(ixSlot);
        cob.tableswitch(0, props.size() - 1, defaultCase, cases);

        for (int i = 0; i < props.size(); i++) {
            cob.labelBinding(caseLabels[i]);
            GenProp prop = props.get(i);
            Class<?> t = prop.type();
            switch (prop.kind()) {
                case STRING -> emitRecordScalar(cob, parser, ctxt, componentSlot[i], t,
                        "getString", MTD_GET_STRING, stockIndex[i]);
                case INT -> emitRecordScalar(cob, parser, ctxt, componentSlot[i], t,
                        "getIntValue", MTD_GET_INT, stockIndex[i]);
                case LONG -> emitRecordScalar(cob, parser, ctxt, componentSlot[i], t,
                        "getLongValue", MTD_GET_LONG, stockIndex[i]);
                case BOOLEAN -> emitRecordScalar(cob, parser, ctxt, componentSlot[i], t,
                        "getBooleanValue", MTD_GET_BOOLEAN, stockIndex[i]);
                case CHILD -> {
                    Label childStock = cob.newLabel();
                    Label childDone = cob.newLabel();
                    cob.aload(parser).invokevirtual(CD_JSON_PARSER, "nextToken", MTD_NEXT_TOKEN);
                    cob.getstatic(CD_JSON_TOKEN, "START_OBJECT", CD_JSON_TOKEN);
                    cob.if_acmpne(childStock);
                    emitChildCall(cob, parser, ctxt, childIndex[i]);
                    cob.checkcast(t.describeConstable().orElseThrow());
                    storeLocal(cob, t, componentSlot[i]);
                    cob.goto_(childDone);
                    cob.labelBinding(childStock);
                    emitStockValueToLocal(cob, parser, ctxt, componentSlot[i], t, stockIndex[i]);
                    cob.labelBinding(childDone);
                }
                case STOCK -> {
                    cob.aload(parser).invokevirtual(CD_JSON_PARSER, "nextToken", MTD_NEXT_TOKEN).pop();
                    emitStockValueToLocal(cob, parser, ctxt, componentSlot[i], t, stockIndex[i]);
                }
            }
            nextNameMatch(cob, parser, matcherSlot, ixSlot);
            cob.goto_(loop);
        }

        cob.labelBinding(defaultCase);
        throwIse(cob, "bad property index");

        cob.labelBinding(endObject);
        cob.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT,
                ConstantDescs.DEFAULT_NAME, ConstantDescs.CD_MethodHandle, ctorIndex));
        ClassDesc[] paramDescs = new ClassDesc[props.size()];
        for (int i = 0; i < props.size(); i++) {
            Class<?> t = props.get(i).type();
            paramDescs[i] = t.describeConstable().orElseThrow();
            if (t == long.class) {
                cob.lload(componentSlot[i]);
            } else if (t == double.class) {
                cob.dload(componentSlot[i]);
            } else if (t == float.class) {
                cob.fload(componentSlot[i]);
            } else if (t.isPrimitive()) {
                cob.iload(componentSlot[i]);
            } else {
                cob.aload(componentSlot[i]);
            }
        }
        cob.invokevirtual(ConstantDescs.CD_MethodHandle, "invokeExact",
                MethodTypeDesc.of(recordDesc, paramDescs));
        cob.areturn();

        cob.labelBinding(unknown);
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "nextToken", MTD_NEXT_TOKEN).pop();
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "skipChildren", MTD_SKIP_CHILDREN).pop();
        nextNameMatch(cob, parser, matcherSlot, ixSlot);
        cob.goto_(loop);

        cob.labelBinding(oddToken);
        cob.aload(0).aload(parser).aload(ctxt)
           .invokevirtual(CD_BASE, "_unexpectedToken", MTD_DESERIALIZE)
           .areturn();
    }

    private static void emitRecordScalar(CodeBuilder cob, int parser, int ctxt, int slot,
            Class<?> type, String getter, MethodTypeDesc getterType, int stockIdx) {
        Label isNull = cob.newLabel();
        Label done = cob.newLabel();
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "nextToken", MTD_NEXT_TOKEN);
        cob.getstatic(CD_JSON_TOKEN, "VALUE_NULL", CD_JSON_TOKEN);
        cob.if_acmpeq(isNull);
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, getter, getterType);
        storeLocal(cob, type, slot);
        cob.goto_(done);
        cob.labelBinding(isNull);
        emitStockValueToLocal(cob, parser, ctxt, slot, type, stockIdx);
        cob.labelBinding(done);
    }

    // Reads the whole value through the stock property (parser positioned on
    // the value token), then converts the boxed result to the component type.
    private static void emitStockValueToLocal(CodeBuilder cob, int parser, int ctxt, int slot,
            Class<?> type, int stockIdx) {
        cob.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT,
                ConstantDescs.DEFAULT_NAME, CD_SETTABLE_PROP, stockIdx));
        cob.aload(parser).aload(ctxt);
        cob.invokevirtual(CD_SETTABLE_PROP, "deserialize", MTD_PROP_DESERIALIZE);
        if (type.isPrimitive()) {
            Class<?> box = ClassUtil.wrapperType(type);
            ClassDesc boxDesc = box.describeConstable().orElseThrow();
            cob.checkcast(boxDesc);
            cob.invokevirtual(boxDesc, type.getName() + "Value",
                    MethodTypeDesc.of(type.describeConstable().orElseThrow()));
        } else {
            cob.checkcast(type.describeConstable().orElseThrow());
        }
        storeLocal(cob, type, slot);
    }

    private static void storeLocal(CodeBuilder cob, Class<?> type, int slot) {
        if (type == long.class) {
            cob.lstore(slot);
        } else if (type == double.class) {
            cob.dstore(slot);
        } else if (type == float.class) {
            cob.fstore(slot);
        } else if (type.isPrimitive()) {
            cob.istore(slot);
        } else {
            cob.astore(slot);
        }
    }

    // The null branch enters deserializeAndSet with the parser already on the
    // VALUE_NULL token, which is the position that method expects. A
    // non-public setter arrives as a classData MethodHandle instead of a
    // direct call.
    private static void emitScalar(CodeBuilder cob, int beanSlot, int parser, int ctxt,
            ClassDesc beanDesc, GenProp prop, String getter, MethodTypeDesc getterType,
            ClassDesc valueDesc, int stockIdx, int setterMhIdx) {
        Label isNull = cob.newLabel();
        Label done = cob.newLabel();
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "nextToken", MTD_NEXT_TOKEN);
        cob.getstatic(CD_JSON_TOKEN, "VALUE_NULL", CD_JSON_TOKEN);
        cob.if_acmpeq(isNull);
        if (setterMhIdx >= 0) {
            cob.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT,
                    ConstantDescs.DEFAULT_NAME, ConstantDescs.CD_MethodHandle, setterMhIdx));
            cob.aload(beanSlot);
            cob.aload(parser).invokevirtual(CD_JSON_PARSER, getter, getterType);
            cob.invokevirtual(ConstantDescs.CD_MethodHandle, "invokeExact",
                    MethodTypeDesc.of(ConstantDescs.CD_void, beanDesc, valueDesc));
        } else {
            cob.aload(beanSlot);
            cob.aload(parser).invokevirtual(CD_JSON_PARSER, getter, getterType);
            invokeSetter(cob, beanDesc, prop, valueDesc);
        }
        cob.goto_(done);
        cob.labelBinding(isNull);
        emitStockSet(cob, parser, ctxt, beanSlot, stockIdx);
        cob.labelBinding(done);
    }

    private static void emitChildCall(CodeBuilder cob, int parser, int ctxt, int childIdx) {
        cob.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT,
                ConstantDescs.DEFAULT_NAME, CD_BASE, childIdx));
        cob.aload(parser).aload(ctxt);
        cob.invokevirtual(CD_BASE, "deserialize", MTD_DESERIALIZE);
    }

    private static void emitStockSet(CodeBuilder cob, int parser, int ctxt, int beanSlot, int stockIdx) {
        cob.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT,
                ConstantDescs.DEFAULT_NAME, CD_SETTABLE_PROP, stockIdx));
        cob.aload(parser).aload(ctxt).aload(beanSlot);
        cob.invokevirtual(CD_SETTABLE_PROP, "deserializeAndSet", MTD_DESERIALIZE_AND_SET);
    }

    private static void invokeSetter(CodeBuilder cob, ClassDesc beanDesc, GenProp prop, ClassDesc valueDesc) {
        MethodTypeDesc setter = prop.setter().getReturnType() == void.class
                ? MethodTypeDesc.of(ConstantDescs.CD_void, valueDesc)
                : MethodTypeDesc.of(prop.setter().getReturnType().describeConstable().orElseThrow(), valueDesc);
        cob.invokevirtual(beanDesc, prop.setter().getName(), setter);
        if (prop.setter().getReturnType() != void.class) {
            cob.pop();
        }
    }

    private static void nextNameMatch(CodeBuilder cob, int parser, int matcherSlot, int ixSlot) {
        cob.aload(parser).aload(matcherSlot)
                .invokevirtual(CD_JSON_PARSER, "nextNameMatch", MTD_NEXT_NAME_MATCH)
                .istore(ixSlot);
    }

    private static void throwIse(CodeBuilder cob, String message) {
        cob.new_(CD_ISE).dup().ldc(message)
                .invokespecial(CD_ISE, ConstantDescs.INIT_NAME,
                        MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String))
                .athrow();
    }
}
