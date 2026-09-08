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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.util.ClassUtil;
import tools.jackson.module.blackbird.internal.GeneratedCodecBase;

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
    // a non-public setter, or when field carries a public field, instead);
    // type is the record component type in record mode and the child value
    // type for CHILD; child is the linked generated codec for CHILD; field is
    // set for a public field stored through putfield.
    public record GenProp(String name, Kind kind, Method setter, SettableBeanProperty stock,
            Class<?> type, GeneratedCodecBase child, MethodHandle setterHandle, Field field) {
        public GenProp(String name, Kind kind, Method setter, SettableBeanProperty stock) {
            this(name, kind, setter, stock, null, null, null, null);
        }

        public GenProp(String name, Kind kind, Method setter, SettableBeanProperty stock,
                Class<?> type) {
            this(name, kind, setter, stock, type, null, null, null);
        }

        public GenProp(String name, Kind kind, Method setter, SettableBeanProperty stock,
                Class<?> type, GeneratedCodecBase child, MethodHandle setterHandle) {
            this(name, kind, setter, stock, type, child, setterHandle, null);
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
    private static final MethodTypeDesc MTD_HANDLE_UNKNOWN = MethodTypeDesc.of(
            ConstantDescs.CD_void, CD_JSON_PARSER, CD_DESER_CONTEXT, ConstantDescs.CD_Object);
    private static final MethodTypeDesc MTD_CHECK_SEEN = MethodTypeDesc.of(
            ConstantDescs.CD_void, CD_DESER_CONTEXT, ConstantDescs.CD_long, ConstantDescs.CD_int);
    private static final ClassDesc CD_EXCEPTION = ClassDesc.of("java.lang.Exception");
    private static final MethodTypeDesc MTD_PROP_WRAP = MethodTypeDesc.of(
            ClassDesc.of("java.lang.RuntimeException"), ClassDesc.of("java.lang.Throwable"),
            ConstantDescs.CD_Object, CD_SETTABLE_PROP, CD_DESER_CONTEXT);

    private BeanCodecGenerator() {}

    public static ValueDeserializer<Object> generate(Class<?> beanClass, List<GenProp> props,
            PropertyNameMatcher matcher, BeanDeserializerBase fallback,
            MethodHandles.Lookup defineLookup)
            throws ReflectiveOperationException {
        return generate(beanClass, props, matcher, fallback, null, null, defineLookup);
    }

    // recordCtor non-null selects record mode: props are in canonical
    // constructor order, values collect into typed locals, and the
    // constructor MethodHandle (exact component signature) builds the value.
    public static ValueDeserializer<Object> generate(Class<?> beanClass, List<GenProp> props,
            PropertyNameMatcher matcher, BeanDeserializerBase fallback, MethodHandle recordCtor,
            MethodHandles.Lookup defineLookup)
            throws ReflectiveOperationException {
        return generate(beanClass, props, matcher, fallback, recordCtor, null, defineLookup);
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
            BuilderSupport builder, MethodHandles.Lookup defineLookup)
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

        // Non-public beans define in the bean's package context (the caller
        // supplies a privateLookupIn of the bean class), which makes the
        // generated new/invokevirtual/putfield instructions legal in-package.
        // Public beans keep the module's own context.
        MethodHandles.Lookup definer =
                (defineLookup != null) ? defineLookup : MethodHandles.lookup();
        byte[] bytes = buildClass(definer.lookupClass().getPackageName(), beanClass, props,
                stockIndex, childIndex, setterMhIndex,
                ctorIndex, builder == null ? null : builder.builderClass(), instIndex, buildIndex);
        CodegenDump.dump(beanClass, "codec", bytes);
        // No ClassOption.STRONG: the codec instance held by the mapper's
        // deserializer cache anchors the class, so codecs unload with the
        // mapper instead of pinning metaspace for the loader's lifetime.
        MethodHandles.Lookup hidden;
        try {
            hidden = definer.defineHiddenClassWithClassData(
                    bytes, List.copyOf(classData), true);
        } catch (IllegalAccessException | SecurityException | LinkageError e) {
            if (defineLookup == null) {
                // Module-context defines never legitimately fail: a generator bug.
                if (e instanceof IllegalAccessException iae) {
                    throw iae;
                }
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw (LinkageError) e;
            }
            // Bean-context defines can fail where the bean's module does not
            // read this module at all (the exported .internal package holds
            // the supertype): an environment gate, stock path.
            return null;
        }
        MethodHandle ctor = hidden.findConstructor(hidden.lookupClass(),
                MethodType.methodType(void.class, BeanDeserializerBase.class));
        try {
            return (ValueDeserializer<Object>) ctor.invoke(fallback);
        } catch (Throwable t) {
            throw new IllegalStateException("cannot instantiate generated codec", t);
        }
    }

    private static byte[] buildClass(String targetPackage, Class<?> beanClass,
            List<GenProp> props,
            int[] stockIndex, int[] childIndex, int[] setterMhIndex,
            int ctorIndex, Class<?> builderClass, int instIndex, int buildIndex) {
        // A hidden class must be named in its define context's package.
        ClassDesc thisClass = ClassDesc.of(
                targetPackage + ".BBCodec_" + beanClass.getSimpleName());
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
        final int propSlot = 6;
        final int excSlot = 7;
        Label propHandler = cob.newLabel();

        ClassDesc beanDesc = beanClass.describeConstable().orElseThrow();

        emitEntryGuard(cob, parser, ctxt);

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
            Label armEnd = beginArm(cob, stockIndex[i], propSlot, propHandler);
            GenProp prop = props.get(i);
            switch (prop.kind()) {
                case STRING -> emitScalar(cob, beanSlot, parser, ctxt, beanDesc, prop,
                        "getString", MTD_GET_STRING, ConstantDescs.CD_String,
                        stockIndex[i], setterMhIndex[i], "VALUE_STRING");
                case INT -> emitScalar(cob, beanSlot, parser, ctxt, beanDesc, prop,
                        "getIntValue", MTD_GET_INT, ConstantDescs.CD_int,
                        stockIndex[i], setterMhIndex[i], "VALUE_NUMBER_INT");
                case LONG -> emitScalar(cob, beanSlot, parser, ctxt, beanDesc, prop,
                        "getLongValue", MTD_GET_LONG, ConstantDescs.CD_long,
                        stockIndex[i], setterMhIndex[i], "VALUE_NUMBER_INT");
                case BOOLEAN -> emitScalar(cob, beanSlot, parser, ctxt, beanDesc, prop,
                        "getBooleanValue", MTD_GET_BOOLEAN, ConstantDescs.CD_boolean,
                        stockIndex[i], setterMhIndex[i], null);
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
                    emitStore(cob, beanDesc, prop, childType);
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
            cob.labelBinding(armEnd);
            nextNameMatch(cob, parser, matcherSlot, ixSlot);
            cob.goto_(loop);
        }

        cob.labelBinding(defaultCase);
        throwIse(cob, "bad property index");

        cob.labelBinding(endObject);
        cob.aload(beanSlot).areturn();

        cob.labelBinding(unknown);
        cob.aload(0).aload(parser).aload(ctxt);
        cob.aload(beanSlot);
        cob.invokevirtual(CD_BASE, "_handleUnknown", MTD_HANDLE_UNKNOWN);
        nextNameMatch(cob, parser, matcherSlot, ixSlot);
        cob.goto_(loop);

        cob.labelBinding(oddToken);
        cob.aload(0).aload(parser).aload(ctxt)
           .invokevirtual(CD_BASE, "_unexpectedToken", MTD_DESERIALIZE)
           .areturn();

        emitPropertyHandler(cob, propHandler, ctxt, propSlot, excSlot, beanSlot);
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
        final int propSlot = 6;
        final int excSlot = 7;
        Label propHandler = cob.newLabel();

        ClassDesc builderDesc = builderClass.describeConstable().orElseThrow();

        emitEntryGuard(cob, parser, ctxt);

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
            Label armEnd = beginArm(cob, stockIndex[i], propSlot, propHandler);
            GenProp prop = props.get(i);
            switch (prop.kind()) {
                case STRING -> emitBuilderScalar(cob, parser, ctxt, builderSlot, builderDesc, prop,
                        "getString", MTD_GET_STRING, ConstantDescs.CD_String, stockIndex[i],
                        "VALUE_STRING");
                case INT -> emitBuilderScalar(cob, parser, ctxt, builderSlot, builderDesc, prop,
                        "getIntValue", MTD_GET_INT, ConstantDescs.CD_int, stockIndex[i],
                        "VALUE_NUMBER_INT");
                case LONG -> emitBuilderScalar(cob, parser, ctxt, builderSlot, builderDesc, prop,
                        "getLongValue", MTD_GET_LONG, ConstantDescs.CD_long, stockIndex[i],
                        "VALUE_NUMBER_INT");
                case BOOLEAN -> emitBuilderScalar(cob, parser, ctxt, builderSlot, builderDesc, prop,
                        "getBooleanValue", MTD_GET_BOOLEAN, ConstantDescs.CD_boolean, stockIndex[i],
                        null);
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
            cob.labelBinding(armEnd);
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
        cob.aload(0).aload(parser).aload(ctxt);
        cob.aload(builderSlot);
        cob.invokevirtual(CD_BASE, "_handleUnknown", MTD_HANDLE_UNKNOWN);
        nextNameMatch(cob, parser, matcherSlot, ixSlot);
        cob.goto_(loop);

        cob.labelBinding(oddToken);
        cob.aload(0).aload(parser).aload(ctxt)
           .invokevirtual(CD_BASE, "_unexpectedToken", MTD_DESERIALIZE)
           .areturn();

        emitPropertyHandler(cob, propHandler, ctxt, propSlot, excSlot, builderSlot);
    }

    private static void emitBuilderScalar(CodeBuilder cob, int parser, int ctxt, int builderSlot,
            ClassDesc builderDesc, GenProp prop, String getter, MethodTypeDesc getterType,
            ClassDesc valueDesc, int stockIdx, String expectedToken) {
        Label useStock = cob.newLabel();
        Label done = cob.newLabel();
        emitExpectedTokenCheck(cob, parser, expectedToken, useStock);
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
        cob.labelBinding(useStock);
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
        final int ixSlot = next++;
        final int seenSlot = next;
        final int propSlot = next + 2;
        final int excSlot = next + 3;
        Label propHandler = cob.newLabel();

        ClassDesc recordDesc = beanClass.describeConstable().orElseThrow();

        emitEntryGuard(cob, parser, ctxt);

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
        cob.lconst_0().lstore(seenSlot);

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
            Label armEnd = beginArm(cob, stockIndex[i], propSlot, propHandler);
            GenProp prop = props.get(i);
            Class<?> t = prop.type();
            switch (prop.kind()) {
                case STRING -> emitRecordScalar(cob, parser, ctxt, componentSlot[i], t,
                        "getString", MTD_GET_STRING, stockIndex[i], "VALUE_STRING");
                case INT -> emitRecordScalar(cob, parser, ctxt, componentSlot[i], t,
                        "getIntValue", MTD_GET_INT, stockIndex[i], "VALUE_NUMBER_INT");
                case LONG -> emitRecordScalar(cob, parser, ctxt, componentSlot[i], t,
                        "getLongValue", MTD_GET_LONG, stockIndex[i], "VALUE_NUMBER_INT");
                case BOOLEAN -> emitRecordScalar(cob, parser, ctxt, componentSlot[i], t,
                        "getBooleanValue", MTD_GET_BOOLEAN, stockIndex[i], null);
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
            cob.labelBinding(armEnd);
            cob.lload(seenSlot);
            cob.loadConstant(1L << i);
            cob.lor();
            cob.lstore(seenSlot);
            nextNameMatch(cob, parser, matcherSlot, ixSlot);
            cob.goto_(loop);
        }

        cob.labelBinding(defaultCase);
        throwIse(cob, "bad property index");

        cob.labelBinding(endObject);
        // Missing components: the cold helper mirrors PropertyValueBuffer's
        // required / FAIL_ON_MISSING_CREATOR_PROPERTIES reporting.
        long allSeen = (props.size() == 64) ? -1L : (1L << props.size()) - 1;
        Label allPresent = cob.newLabel();
        cob.lload(seenSlot);
        cob.loadConstant(allSeen);
        cob.lcmp();
        cob.ifeq(allPresent);
        cob.aload(0).aload(ctxt).lload(seenSlot);
        cob.loadConstant(props.size());
        cob.invokevirtual(CD_BASE, "_checkRecordSeen", MTD_CHECK_SEEN);
        cob.labelBinding(allPresent);
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
        cob.aload(0).aload(parser).aload(ctxt);
        cob.aconst_null();
        cob.invokevirtual(CD_BASE, "_handleUnknown", MTD_HANDLE_UNKNOWN);
        nextNameMatch(cob, parser, matcherSlot, ixSlot);
        cob.goto_(loop);

        cob.labelBinding(oddToken);
        cob.aload(0).aload(parser).aload(ctxt)
           .invokevirtual(CD_BASE, "_unexpectedToken", MTD_DESERIALIZE)
           .areturn();

        emitPropertyHandler(cob, propHandler, ctxt, propSlot, excSlot, -1);
    }

    private static void emitRecordScalar(CodeBuilder cob, int parser, int ctxt, int slot,
            Class<?> type, String getter, MethodTypeDesc getterType, int stockIdx,
            String expectedToken) {
        Label useStock = cob.newLabel();
        Label done = cob.newLabel();
        emitExpectedTokenCheck(cob, parser, expectedToken, useStock);
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, getter, getterType);
        storeLocal(cob, type, slot);
        cob.goto_(done);
        cob.labelBinding(useStock);
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
            ClassDesc valueDesc, int stockIdx, int setterMhIdx, String expectedToken) {
        Label useStock = cob.newLabel();
        Label done = cob.newLabel();
        emitExpectedTokenCheck(cob, parser, expectedToken, useStock);
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
            emitStore(cob, beanDesc, prop, valueDesc);
        }
        cob.goto_(done);
        cob.labelBinding(useStock);
        emitStockSet(cob, parser, ctxt, beanSlot, stockIdx);
        cob.labelBinding(done);
    }

    // Stores a value already on the stack (receiver, then value) into the bean:
    // a putfield for a public field, otherwise the direct setter call.
    private static void emitStore(CodeBuilder cob, ClassDesc beanDesc, GenProp prop,
            ClassDesc valueDesc) {
        if (prop.field() != null) {
            ClassDesc owner = prop.field().getDeclaringClass().describeConstable().orElseThrow();
            cob.putfield(owner, prop.field().getName(), valueDesc);
        } else {
            invokeSetter(cob, beanDesc, prop, valueDesc);
        }
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

    // Delegates to the stock deserializer for any entry the generated loop
    // does not model: a stream not positioned on START_OBJECT (stock also
    // accepts PROPERTY_NAME and other entry shapes) or an active view.
    private static void emitEntryGuard(CodeBuilder cob, int parser, int ctxt) {
        Label delegate = cob.newLabel();
        Label proceed = cob.newLabel();
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "currentToken", MTD_NEXT_TOKEN);
        cob.getstatic(CD_JSON_TOKEN, "START_OBJECT", CD_JSON_TOKEN);
        cob.if_acmpne(delegate);
        cob.aload(ctxt).invokevirtual(CD_DESER_CONTEXT, "getActiveView",
                MethodTypeDesc.of(ConstantDescs.CD_Class));
        cob.ifnull(proceed);
        cob.labelBinding(delegate);
        cob.aload(0).getfield(CD_BASE, "_fallback", CD_BEAN_DESER_BASE);
        cob.aload(parser).aload(ctxt);
        cob.invokevirtual(CD_BEAN_DESER_BASE, "deserialize", MTD_DESERIALIZE);
        cob.areturn();
        cob.labelBinding(proceed);
    }

    // Advances to the value token and branches to useStock unless it is the
    // token the inline read expects. Null, quoted scalars, and mismatched
    // shapes all take the stock property, which owns coercion and null
    // handling. expectedToken null selects the boolean pair.
    private static void emitExpectedTokenCheck(CodeBuilder cob, int parser,
            String expectedToken, Label useStock) {
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "nextToken", MTD_NEXT_TOKEN);
        if (expectedToken != null) {
            cob.getstatic(CD_JSON_TOKEN, expectedToken, CD_JSON_TOKEN);
            cob.if_acmpne(useStock);
        } else {
            Label isTrue = cob.newLabel();
            Label fast = cob.newLabel();
            cob.dup();
            cob.getstatic(CD_JSON_TOKEN, "VALUE_TRUE", CD_JSON_TOKEN);
            cob.if_acmpeq(isTrue);
            cob.getstatic(CD_JSON_TOKEN, "VALUE_FALSE", CD_JSON_TOKEN);
            cob.if_acmpne(useStock);
            cob.goto_(fast);
            cob.labelBinding(isTrue);
            cob.pop();
            cob.labelBinding(fast);
        }
    }

    // Loads the arm's stock property into propSlot and opens its exception
    // region: any Exception from a property arm is rethrown with the property
    // reference prepended, like the stock loop's wrapAndThrow.
    private static Label beginArm(CodeBuilder cob, int stockIdx, int propSlot, Label handler) {
        cob.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT,
                ConstantDescs.DEFAULT_NAME, CD_SETTABLE_PROP, stockIdx));
        cob.astore(propSlot);
        Label armStart = cob.newLabel();
        Label armEnd = cob.newLabel();
        cob.exceptionCatch(armStart, armEnd, handler, CD_EXCEPTION);
        cob.labelBinding(armStart);
        return armEnd;
    }

    // receiverSlot < 0 selects aconst_null (record mode: no instance yet).
    private static void emitPropertyHandler(CodeBuilder cob, Label handler, int ctxt,
            int propSlot, int excSlot, int receiverSlot) {
        cob.labelBinding(handler);
        cob.astore(excSlot);
        cob.aload(0);
        cob.aload(excSlot);
        if (receiverSlot < 0) {
            cob.aconst_null();
        } else {
            cob.aload(receiverSlot);
        }
        cob.aload(propSlot);
        cob.aload(ctxt);
        cob.invokevirtual(CD_BASE, "_propertyException", MTD_PROP_WRAP);
        cob.athrow();
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
