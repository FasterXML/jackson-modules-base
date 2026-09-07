package tools.jackson.module.blackbird.codegen;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.attribute.MethodParameterInfo;
import java.lang.classfile.attribute.MethodParametersAttribute;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.util.ClassUtil;
import tools.jackson.module.blackbird.internal.GeneratedReaderBase;

/**
 * Emits a hidden-class deserializer for one bean: a loop on nextNameMatch, a
 * tableswitch on the property index, inlined scalar reads with direct setter
 * calls for eligible properties, and the stock SettableBeanProperty (a named
 * classData constant) for everything else. The matcher and the per-property
 * payloads travel as a name-addressed classData map; the fallback
 * deserializer is a constructor argument consumed by
 * {@link GeneratedReaderBase}.
 */
public final class BeanReaderGenerator
{
    public enum Kind { STRING, INT, LONG, BOOLEAN, CHILD, STOCK }

    // How a generated codec treats an active view. NONE: views cannot affect
    // this bean (the stock (de)serializer would ignore them too), no view code.
    // DELEGATE: an active view hands the whole call to the stock fallback
    // (beans with more than 64 properties). MASK: the codec resolves a cached
    // per-view visibility bitmask and each property arm tests its bit, so
    // view-active calls keep the generated fast path.
    public enum ViewStrategy { NONE, DELEGATE, MASK }

    // setter applies to POJO and builder modes (null when setterHandle carries
    // a non-public setter, or when field carries a public field, instead);
    // type is the record component type in record mode and the child value
    // type for CHILD; child is the linked generated codec for CHILD; field is
    // set for a public field stored through putfield.
    public record GenProp(String name, Kind kind, Method setter, SettableBeanProperty stock,
            Class<?> type, GeneratedReaderBase child, MethodHandle setterHandle, Field field) {
        public GenProp(String name, Kind kind, Method setter, SettableBeanProperty stock) {
            this(name, kind, setter, stock, null, null, null, null);
        }

        public GenProp(String name, Kind kind, Method setter, SettableBeanProperty stock,
                Class<?> type) {
            this(name, kind, setter, stock, type, null, null, null);
        }

        public GenProp(String name, Kind kind, Method setter, SettableBeanProperty stock,
                Class<?> type, GeneratedReaderBase child, MethodHandle setterHandle) {
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
    // literal makes sure that GeneratedReaderBase.class is present in the test
    // module, which shadows target/classes at run time. Every same-module
    // class that generated code names only as a string needs such a
    // compile-time reference.
    private static final ClassDesc CD_BASE =
            GeneratedReaderBase.class.describeConstable().orElseThrow();
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
    private static final ClassDesc CD_SET = java.util.Set.class.describeConstable().orElseThrow();
    private static final MethodTypeDesc MTD_CTOR4 = MethodTypeDesc.of(ConstantDescs.CD_void,
            CD_BEAN_DESER_BASE, ConstantDescs.CD_boolean, CD_SET, CD_SET);
    private static final MethodTypeDesc MTD_HANDLE_UNKNOWN = MethodTypeDesc.of(
            ConstantDescs.CD_void, CD_JSON_PARSER, CD_DESER_CONTEXT, ConstantDescs.CD_Object);
    private static final MethodTypeDesc MTD_CHECK_SEEN = MethodTypeDesc.of(
            ConstantDescs.CD_void, CD_DESER_CONTEXT, ConstantDescs.CD_long, ConstantDescs.CD_int);
    private static final MethodTypeDesc MTD_GET_ACTIVE_VIEW =
            MethodTypeDesc.of(ConstantDescs.CD_Class);
    private static final MethodTypeDesc MTD_VIEW_MASK =
            MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_Class);
    private static final MethodTypeDesc MTD_HIDDEN_VIEW = MethodTypeDesc.of(
            ConstantDescs.CD_void, CD_JSON_PARSER, CD_DESER_CONTEXT, CD_SETTABLE_PROP);
    private static final MethodTypeDesc MTD_VISIBLE_IN_VIEW =
            MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_Class);
    private static final ClassDesc CD_EXCEPTION = ClassDesc.of("java.lang.Exception");
    private static final MethodTypeDesc MTD_PROP_WRAP = MethodTypeDesc.of(
            ClassDesc.of("java.lang.RuntimeException"), ClassDesc.of("java.lang.Throwable"),
            ConstantDescs.CD_Object, CD_SETTABLE_PROP, CD_DESER_CONTEXT);

    // Generated code resolves class-data entries by NAME through the
    // classDataEntry bootstrap on the base class (the JDK classDataAt
    // bootstrap rejects any condy name but "_"); see dataName for the naming.
    private static final java.lang.constant.DirectMethodHandleDesc BSM_DATA_ENTRY =
            ConstantDescs.ofConstantBootstrap(CD_BASE, "classDataEntry", ConstantDescs.CD_Object);

    private BeanReaderGenerator() {}

    static void ldcData(CodeBuilder cob, String name, ClassDesc type) {
        cob.ldc(DynamicConstantDesc.ofNamed(BSM_DATA_ENTRY, name, type));
    }

    // Condy names are JVM unqualified names ('.', ';', '[', '/' forbidden) and
    // JSON property names are arbitrary, so sanitize and dedupe.
    static String dataName(Map<String, Object> data, String base) {
        StringBuilder sb = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            sb.append(c == '.' || c == ';' || c == '[' || c == '/' ? '$' : c);
        }
        String name = sb.toString();
        String candidate = name;
        for (int n = 2; data.containsKey(candidate); n++) {
            candidate = name + "$" + n;
        }
        return candidate;
    }

    public static ValueDeserializer<Object> generate(Class<?> beanClass, List<GenProp> props,
            PropertyNameMatcher matcher, BeanDeserializerBase fallback,
            MethodHandles.Lookup defineLookup, ViewStrategy views, Ignorals ignorals,
            int[] aliasArms)
            throws ReflectiveOperationException {
        return generate(beanClass, props, matcher, fallback, null, null, defineLookup, views,
                ignorals, aliasArms);
    }

    // recordCtor non-null selects record mode: props are in canonical
    // constructor order, values collect into typed locals, and the
    // constructor MethodHandle (exact component signature) builds the value.
    public static ValueDeserializer<Object> generate(Class<?> beanClass, List<GenProp> props,
            PropertyNameMatcher matcher, BeanDeserializerBase fallback, MethodHandle recordCtor,
            MethodHandles.Lookup defineLookup, ViewStrategy views, Ignorals ignorals,
            int[] aliasArms)
            throws ReflectiveOperationException {
        return generate(beanClass, props, matcher, fallback, recordCtor, null, defineLookup, views,
                ignorals, aliasArms);
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
            BuilderSupport builder, MethodHandles.Lookup defineLookup, ViewStrategy views,
            Ignorals ignorals, int[] aliasArms)
            throws ReflectiveOperationException {
        Map<String, Object> classData = new LinkedHashMap<>();
        classData.put("matcher", matcher);
        // Tier-A properties also carry their SettableBeanProperty: their
        // VALUE_NULL branch runs through it, since null handling is
        // configuration-dependent per property.
        String[] stockName = new String[props.size()];
        String[] childName = new String[props.size()];
        String[] setterName = new String[props.size()];
        for (int i = 0; i < props.size(); i++) {
            GenProp gp = props.get(i);
            stockName[i] = dataName(classData, gp.name() + "Prop");
            classData.put(stockName[i], gp.stock());
            if (gp.child() != null) {
                childName[i] = dataName(classData, gp.name() + "Codec");
                classData.put(childName[i], gp.child());
            }
            if (gp.setterHandle() != null) {
                setterName[i] = dataName(classData, gp.name() + "Setter");
                classData.put(setterName[i], gp.setterHandle());
            }
        }
        if (recordCtor != null) {
            classData.put("constructor", recordCtor);
        }
        if (builder != null) {
            classData.put("instantiator", builder.instantiator());
            classData.put("buildMethod", builder.buildMethod());
        }

        // Non-public beans define in the bean's package context (the caller
        // supplies a privateLookupIn of the bean class), which makes the
        // generated new/invokevirtual/putfield instructions legal in-package.
        // Public beans keep the module's own context.
        MethodHandles.Lookup definer =
                (defineLookup != null) ? defineLookup : MethodHandles.lookup();
        byte[] bytes = buildClass(definer.lookupClass().getPackageName(), beanClass, props,
                stockName, childName, setterName, recordCtor != null,
                builder == null ? null : builder.builderClass(), views, aliasArms);
        CodegenDump.dump(beanClass, "reader", bytes);
        // No ClassOption.STRONG: the codec instance held by the mapper's
        // deserializer cache anchors the class, so codecs unload with the
        // mapper instead of pinning metaspace for the loader's lifetime.
        MethodHandles.Lookup hidden;
        try {
            hidden = definer.defineHiddenClassWithClassData(
                    bytes, Map.copyOf(classData), true);
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
                MethodType.methodType(void.class, BeanDeserializerBase.class,
                        boolean.class, Set.class, Set.class));
        Ignorals ig = (ignorals == null) ? Ignorals.NONE : ignorals;
        try {
            return (ValueDeserializer<Object>) ctor.invoke(fallback,
                    ig.ignoreAllUnknown(), ig.ignorableProps(), ig.includableProps());
        } catch (Throwable t) {
            throw new IllegalStateException("cannot instantiate generated codec", t);
        }
    }

    /** Ignoral configuration carried from the modifier into the codec. */
    public record Ignorals(boolean ignoreAllUnknown, Set<String> ignorableProps,
            Set<String> includableProps) {
        public static final Ignorals NONE = new Ignorals(false, null, null);
    }

    private static byte[] buildClass(String targetPackage, Class<?> beanClass,
            List<GenProp> props,
            String[] stockName, String[] childName, String[] setterName,
            boolean recordMode, Class<?> builderClass,
            ViewStrategy views, int[] aliasArms) {
        // A hidden class must be named in its define context's package.
        ClassDesc thisClass = ClassDesc.of(
                targetPackage + ".BBReader_" + beanClass.getSimpleName());
        return ClassFile.of().build(thisClass, clb -> {
            clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            clb.withSuperclass(CD_BASE);
            clb.withMethod(ConstantDescs.INIT_NAME, MTD_CTOR4, ClassFile.ACC_PUBLIC,
                    mb -> mb.with(params("fallback", "ignoreAllUnknown", "ignorableProps",
                                    "includableProps"))
                            .withCode(cob -> cob.aload(0).aload(1).iload(2).aload(3).aload(4)
                                    .invokespecial(CD_BASE, ConstantDescs.INIT_NAME, MTD_CTOR4)
                                    .return_()));
            clb.withMethod("deserialize", MTD_DESERIALIZE, ClassFile.ACC_PUBLIC,
                    mb -> mb.with(params("p", "ctxt")).withCode(cob -> {
                        if (builderClass != null) {
                            buildBuilderDeserialize(cob, builderClass, props, stockName,
                                    childName, views, aliasArms);
                        } else if (!recordMode) {
                            buildDeserialize(cob, beanClass, props, stockName, childName,
                                    setterName, views, aliasArms);
                        } else {
                            buildRecordDeserialize(cob, beanClass, props, stockName, childName,
                                    views, aliasArms);
                        }
                    }));
            if (views == ViewStrategy.MASK) {
                emitComputeViewMask(clb, props, stockName);
            }
        });
    }

    // Debug metadata: parameter names and named locals make dumped codecs
    // (the blackbird.debug.dumpDir output) read like source in a decompiler;
    // the verifier ignores both attributes.
    static MethodParametersAttribute params(String... names) {
        MethodParameterInfo[] infos = new MethodParameterInfo[names.length];
        for (int i = 0; i < names.length; i++) {
            infos[i] = MethodParameterInfo.ofParameter(Optional.of(names[i]), 0);
        }
        return MethodParametersAttribute.of(infos);
    }

    // Overrides GeneratedReaderBase._computeViewMask: bit i set when arm i's
    // stock property is visible in the view, which keeps visibility semantics
    // (matchers, default-view inclusion) exactly the stock ones.
    private static void emitComputeViewMask(java.lang.classfile.ClassBuilder clb,
            List<GenProp> props, String[] stockName) {
        clb.withMethod("_computeViewMask", MTD_VIEW_MASK, ClassFile.ACC_PROTECTED,
                mb -> mb.with(params("activeView")).withCode(cob -> {
            final int maskSlot = 2;
            Label scopeStart = cob.newBoundLabel();
            cob.lconst_0().lstore(maskSlot);
            for (int i = 0; i < props.size(); i++) {
                Label skip = cob.newLabel();
                ldcData(cob, stockName[i], CD_SETTABLE_PROP);
                cob.aload(1);
                cob.invokevirtual(CD_SETTABLE_PROP, "visibleInView", MTD_VISIBLE_IN_VIEW);
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

    // MASK strategy: resolves the visibility bitmask for the call (all-ones
    // when no view is active) into maskSlot.
    private static void emitViewMask(CodeBuilder cob, int ctxt, int maskSlot) {
        Label nullView = cob.newLabel();
        Label haveMask = cob.newLabel();
        cob.aload(ctxt).invokevirtual(CD_DESER_CONTEXT, "getActiveView", MTD_GET_ACTIVE_VIEW);
        cob.dup();
        cob.ifnull(nullView);
        cob.aload(0);
        cob.swap();
        cob.invokevirtual(CD_BASE, "_viewMask", MTD_VIEW_MASK);
        cob.lstore(maskSlot);
        cob.goto_(haveMask);
        cob.labelBinding(nullView);
        cob.pop();
        cob.loadConstant(-1L);
        cob.lstore(maskSlot);
        cob.labelBinding(haveMask);
    }

    // MASK strategy, start of each arm: a hidden property consumes its value
    // with stock semantics (advance to the value token, then the base helper
    // reports or skips) and continues the loop without touching the bean.
    private static void emitArmViewCheck(CodeBuilder cob, int parser, int ctxt, int maskSlot,
            int matcherSlot, int ixSlot, int armIndex, String stockName, Label loop) {
        Label visible = cob.newLabel();
        cob.lload(maskSlot);
        cob.loadConstant(1L << armIndex);
        cob.land();
        cob.lconst_0();
        cob.lcmp();
        cob.ifne(visible);
        cob.aload(0).aload(parser).aload(ctxt);
        ldcData(cob, stockName, CD_SETTABLE_PROP);
        cob.invokevirtual(CD_BASE, "_hiddenView", MTD_HIDDEN_VIEW);
        nextNameMatch(cob, parser, matcherSlot, ixSlot);
        cob.goto_(loop);
        cob.labelBinding(visible);
    }

    private static void buildDeserialize(CodeBuilder cob, Class<?> beanClass,
            List<GenProp> props, String[] stockName, String[] childName, String[] setterName,
            ViewStrategy views, int[] aliasArms) {
        final int parser = 1;
        final int ctxt = 2;
        final int beanSlot = 3;
        final int matcherSlot = 4;
        final int ixSlot = 5;
        final int propSlot = 6;
        final int excSlot = 7;
        final int maskSlot = 8;
        Label propHandler = cob.newLabel();

        ClassDesc beanDesc = beanClass.describeConstable().orElseThrow();

        Label scopeStart = cob.newBoundLabel();
        Label delegate = emitEntryGuard(cob, parser, ctxt, views);
        if (views == ViewStrategy.MASK) {
            emitViewMask(cob, ctxt, maskSlot);
        }

        cob.new_(beanDesc).dup()
           .invokespecial(beanDesc, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
           .astore(beanSlot);
        cob.aload(parser).aload(beanSlot)
           .invokevirtual(CD_JSON_PARSER, "assignCurrentValue", MTD_ASSIGN_CURRENT);

        ldcData(cob, "matcher", CD_NAME_MATCHER);
        cob.astore(matcherSlot);

        Label loop = cob.newLabel();
        Label switchPart = cob.newLabel();
        Label endObject = cob.newLabel();
        Label unknown = cob.newLabel();
        Label oddToken = cob.newLabel();
        Label defaultCase = cob.newLabel();

        emitFirstMatch(cob, parser, matcherSlot, ixSlot);

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
        // Alias matcher indexes follow the primaries and share their arms.
        int totalNames = props.size() + ((aliasArms == null) ? 0 : aliasArms.length);
        for (int k = props.size(); k < totalNames; k++) {
            cases.add(SwitchCase.of(k, caseLabels[aliasArms[k - props.size()]]));
        }
        cob.iload(ixSlot);
        cob.tableswitch(0, totalNames - 1, defaultCase, cases);

        for (int i = 0; i < props.size(); i++) {
            cob.labelBinding(caseLabels[i]);
            if (views == ViewStrategy.MASK) {
                emitArmViewCheck(cob, parser, ctxt, maskSlot, matcherSlot, ixSlot, i,
                        stockName[i], loop);
            }
            Label armEnd = beginArm(cob, stockName[i], propSlot, propHandler);
            GenProp prop = props.get(i);
            switch (prop.kind()) {
                case STRING -> emitScalar(cob, beanSlot, parser, ctxt, beanDesc, prop,
                        "getString", MTD_GET_STRING, ConstantDescs.CD_String,
                        stockName[i], setterName[i], "VALUE_STRING");
                case INT -> emitScalar(cob, beanSlot, parser, ctxt, beanDesc, prop,
                        "getIntValue", MTD_GET_INT, ConstantDescs.CD_int,
                        stockName[i], setterName[i], "VALUE_NUMBER_INT");
                case LONG -> emitScalar(cob, beanSlot, parser, ctxt, beanDesc, prop,
                        "getLongValue", MTD_GET_LONG, ConstantDescs.CD_long,
                        stockName[i], setterName[i], "VALUE_NUMBER_INT");
                case BOOLEAN -> emitScalar(cob, beanSlot, parser, ctxt, beanDesc, prop,
                        "getBooleanValue", MTD_GET_BOOLEAN, ConstantDescs.CD_boolean,
                        stockName[i], setterName[i], null);
                case CHILD -> {
                    Label childStock = cob.newLabel();
                    Label childDone = cob.newLabel();
                    ClassDesc childType = prop.type().describeConstable().orElseThrow();
                    cob.aload(parser).invokevirtual(CD_JSON_PARSER, "currentToken", MTD_NEXT_TOKEN);
                    cob.getstatic(CD_JSON_TOKEN, "START_OBJECT", CD_JSON_TOKEN);
                    cob.if_acmpne(childStock);
                    cob.aload(beanSlot);
                    emitChildCall(cob, parser, ctxt, childName[i]);
                    cob.checkcast(childType);
                    emitStore(cob, beanDesc, prop, childType);
                    cob.goto_(childDone);
                    cob.labelBinding(childStock);
                    emitStockSet(cob, parser, ctxt, beanSlot, stockName[i]);
                    cob.labelBinding(childDone);
                }
                case STOCK -> {
                    emitStockSet(cob, parser, ctxt, beanSlot, stockName[i]);
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
        emitDelegateTail(cob, parser, ctxt, delegate);

        Label scopeEnd = cob.newBoundLabel();
        cob.localVariable(parser, "p", CD_JSON_PARSER, scopeStart, scopeEnd);
        cob.localVariable(ctxt, "ctxt", CD_DESER_CONTEXT, scopeStart, scopeEnd);
        cob.localVariable(beanSlot, "bean", beanDesc, scopeStart, scopeEnd);
        cob.localVariable(matcherSlot, "matcher", CD_NAME_MATCHER, scopeStart, scopeEnd);
        cob.localVariable(ixSlot, "ix", ConstantDescs.CD_int, scopeStart, scopeEnd);
        cob.localVariable(propSlot, "prop", CD_SETTABLE_PROP, scopeStart, scopeEnd);
        cob.localVariable(excSlot, "e", CD_EXCEPTION, scopeStart, scopeEnd);
        if (views == ViewStrategy.MASK) {
            cob.localVariable(maskSlot, "viewMask", ConstantDescs.CD_long, scopeStart, scopeEnd);
        }
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
            List<GenProp> props, String[] stockName, String[] childName,
            ViewStrategy views, int[] aliasArms) {
        final int parser = 1;
        final int ctxt = 2;
        final int builderSlot = 3;
        final int matcherSlot = 4;
        final int ixSlot = 5;
        final int propSlot = 6;
        final int excSlot = 7;
        final int maskSlot = 8;
        Label propHandler = cob.newLabel();

        ClassDesc builderDesc = builderClass.describeConstable().orElseThrow();

        Label scopeStart = cob.newBoundLabel();
        Label delegate = emitEntryGuard(cob, parser, ctxt, views);
        if (views == ViewStrategy.MASK) {
            emitViewMask(cob, ctxt, maskSlot);
        }

        ldcData(cob, "instantiator", CD_VALUE_INSTANTIATOR);
        cob.aload(ctxt);
        cob.invokevirtual(CD_VALUE_INSTANTIATOR, "createUsingDefault", MTD_CREATE_DEFAULT);
        cob.checkcast(builderDesc);
        cob.astore(builderSlot);

        ldcData(cob, "matcher", CD_NAME_MATCHER);
        cob.astore(matcherSlot);

        Label loop = cob.newLabel();
        Label switchPart = cob.newLabel();
        Label endObject = cob.newLabel();
        Label unknown = cob.newLabel();
        Label oddToken = cob.newLabel();
        Label defaultCase = cob.newLabel();

        emitFirstMatch(cob, parser, matcherSlot, ixSlot);

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
        // Alias matcher indexes follow the primaries and share their arms.
        int totalNames = props.size() + ((aliasArms == null) ? 0 : aliasArms.length);
        for (int k = props.size(); k < totalNames; k++) {
            cases.add(SwitchCase.of(k, caseLabels[aliasArms[k - props.size()]]));
        }
        cob.iload(ixSlot);
        cob.tableswitch(0, totalNames - 1, defaultCase, cases);

        for (int i = 0; i < props.size(); i++) {
            cob.labelBinding(caseLabels[i]);
            if (views == ViewStrategy.MASK) {
                emitArmViewCheck(cob, parser, ctxt, maskSlot, matcherSlot, ixSlot, i,
                        stockName[i], loop);
            }
            Label armEnd = beginArm(cob, stockName[i], propSlot, propHandler);
            GenProp prop = props.get(i);
            switch (prop.kind()) {
                case STRING -> emitBuilderScalar(cob, parser, ctxt, builderSlot, builderDesc, prop,
                        "getString", MTD_GET_STRING, ConstantDescs.CD_String, stockName[i],
                        "VALUE_STRING");
                case INT -> emitBuilderScalar(cob, parser, ctxt, builderSlot, builderDesc, prop,
                        "getIntValue", MTD_GET_INT, ConstantDescs.CD_int, stockName[i],
                        "VALUE_NUMBER_INT");
                case LONG -> emitBuilderScalar(cob, parser, ctxt, builderSlot, builderDesc, prop,
                        "getLongValue", MTD_GET_LONG, ConstantDescs.CD_long, stockName[i],
                        "VALUE_NUMBER_INT");
                case BOOLEAN -> emitBuilderScalar(cob, parser, ctxt, builderSlot, builderDesc, prop,
                        "getBooleanValue", MTD_GET_BOOLEAN, ConstantDescs.CD_boolean, stockName[i],
                        null);
                case CHILD -> {
                    Label childStock = cob.newLabel();
                    Label childDone = cob.newLabel();
                    ClassDesc childType = prop.type().describeConstable().orElseThrow();
                    cob.aload(parser).invokevirtual(CD_JSON_PARSER, "currentToken", MTD_NEXT_TOKEN);
                    cob.getstatic(CD_JSON_TOKEN, "START_OBJECT", CD_JSON_TOKEN);
                    cob.if_acmpne(childStock);
                    cob.aload(builderSlot);
                    emitChildCall(cob, parser, ctxt, childName[i]);
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
                    emitStockSetReturn(cob, parser, ctxt, builderSlot, builderDesc, stockName[i]);
                    cob.labelBinding(childDone);
                }
                case STOCK -> {
                    emitStockSetReturn(cob, parser, ctxt, builderSlot, builderDesc, stockName[i]);
                }
            }
            cob.labelBinding(armEnd);
            nextNameMatch(cob, parser, matcherSlot, ixSlot);
            cob.goto_(loop);
        }

        cob.labelBinding(defaultCase);
        throwIse(cob, "bad property index");

        cob.labelBinding(endObject);
        ldcData(cob, "buildMethod", ConstantDescs.CD_MethodHandle);
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
        emitDelegateTail(cob, parser, ctxt, delegate);

        Label scopeEnd = cob.newBoundLabel();
        cob.localVariable(parser, "p", CD_JSON_PARSER, scopeStart, scopeEnd);
        cob.localVariable(ctxt, "ctxt", CD_DESER_CONTEXT, scopeStart, scopeEnd);
        cob.localVariable(builderSlot, "builder", builderDesc, scopeStart, scopeEnd);
        cob.localVariable(matcherSlot, "matcher", CD_NAME_MATCHER, scopeStart, scopeEnd);
        cob.localVariable(ixSlot, "ix", ConstantDescs.CD_int, scopeStart, scopeEnd);
        cob.localVariable(propSlot, "prop", CD_SETTABLE_PROP, scopeStart, scopeEnd);
        cob.localVariable(excSlot, "e", CD_EXCEPTION, scopeStart, scopeEnd);
        if (views == ViewStrategy.MASK) {
            cob.localVariable(maskSlot, "viewMask", ConstantDescs.CD_long, scopeStart, scopeEnd);
        }
    }

    private static void emitBuilderScalar(CodeBuilder cob, int parser, int ctxt, int builderSlot,
            ClassDesc builderDesc, GenProp prop, String getter, MethodTypeDesc getterType,
            ClassDesc valueDesc, String stockName, String expectedToken) {
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
        emitStockSetReturn(cob, parser, ctxt, builderSlot, builderDesc, stockName);
        cob.labelBinding(done);
    }

    // Builder properties apply through deserializeSetAndReturn: fluent
    // builders may return a replacement instance, which becomes the new
    // builder local.
    private static void emitStockSetReturn(CodeBuilder cob, int parser, int ctxt, int builderSlot,
            ClassDesc builderDesc, String stockName) {
        ldcData(cob, stockName, CD_SETTABLE_PROP);
        cob.aload(parser).aload(ctxt).aload(builderSlot);
        cob.invokevirtual(CD_SETTABLE_PROP, "deserializeSetAndReturn", MTD_DESER_SET_RETURN);
        cob.checkcast(builderDesc);
        cob.astore(builderSlot);
    }

    private static void buildRecordDeserialize(CodeBuilder cob, Class<?> beanClass,
            List<GenProp> props, String[] stockName, String[] childName,
            ViewStrategy views, int[] aliasArms) {
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
        final int maskSlot = next + 4;
        final int nullMaskSlot = next + 6;
        Label propHandler = cob.newLabel();

        ClassDesc recordDesc = beanClass.describeConstable().orElseThrow();

        Label scopeStart = cob.newBoundLabel();
        Label delegate = emitEntryGuard(cob, parser, ctxt, views);
        if (views == ViewStrategy.MASK) {
            emitViewMask(cob, ctxt, maskSlot);
        }

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

        ldcData(cob, "matcher", CD_NAME_MATCHER);
        cob.astore(matcherSlot);

        Label loop = cob.newLabel();
        Label switchPart = cob.newLabel();
        Label endObject = cob.newLabel();
        Label unknown = cob.newLabel();
        Label oddToken = cob.newLabel();
        Label defaultCase = cob.newLabel();

        emitFirstMatch(cob, parser, matcherSlot, ixSlot);

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
        // Alias matcher indexes follow the primaries and share their arms.
        int totalNames = props.size() + ((aliasArms == null) ? 0 : aliasArms.length);
        for (int k = props.size(); k < totalNames; k++) {
            cases.add(SwitchCase.of(k, caseLabels[aliasArms[k - props.size()]]));
        }
        cob.iload(ixSlot);
        cob.tableswitch(0, totalNames - 1, defaultCase, cases);

        for (int i = 0; i < props.size(); i++) {
            cob.labelBinding(caseLabels[i]);
            if (views == ViewStrategy.MASK) {
                // A hidden component stays unseen, so required and
                // missing-property reporting treat it exactly like an absent
                // property, matching the stock creator path.
                emitArmViewCheck(cob, parser, ctxt, maskSlot, matcherSlot, ixSlot, i,
                        stockName[i], loop);
            }
            Label armEnd = beginArm(cob, stockName[i], propSlot, propHandler);
            GenProp prop = props.get(i);
            Class<?> t = prop.type();
            switch (prop.kind()) {
                case STRING -> emitRecordScalar(cob, parser, ctxt, componentSlot[i], t,
                        "getString", MTD_GET_STRING, stockName[i], "VALUE_STRING");
                case INT -> emitRecordScalar(cob, parser, ctxt, componentSlot[i], t,
                        "getIntValue", MTD_GET_INT, stockName[i], "VALUE_NUMBER_INT");
                case LONG -> emitRecordScalar(cob, parser, ctxt, componentSlot[i], t,
                        "getLongValue", MTD_GET_LONG, stockName[i], "VALUE_NUMBER_INT");
                case BOOLEAN -> emitRecordScalar(cob, parser, ctxt, componentSlot[i], t,
                        "getBooleanValue", MTD_GET_BOOLEAN, stockName[i], null);
                case CHILD -> {
                    Label childStock = cob.newLabel();
                    Label childDone = cob.newLabel();
                    cob.aload(parser).invokevirtual(CD_JSON_PARSER, "currentToken", MTD_NEXT_TOKEN);
                    cob.getstatic(CD_JSON_TOKEN, "START_OBJECT", CD_JSON_TOKEN);
                    cob.if_acmpne(childStock);
                    emitChildCall(cob, parser, ctxt, childName[i]);
                    cob.checkcast(t.describeConstable().orElseThrow());
                    storeLocal(cob, t, componentSlot[i]);
                    cob.goto_(childDone);
                    cob.labelBinding(childStock);
                    emitStockValueToLocal(cob, parser, ctxt, componentSlot[i], t, stockName[i]);
                    cob.labelBinding(childDone);
                }
                case STOCK -> {
                    emitStockValueToLocal(cob, parser, ctxt, componentSlot[i], t, stockName[i]);
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
        // Reference components that ended null (missing or explicit) feed the
        // cold FAIL_ON_NULL_CREATOR_PROPERTIES check, mirroring
        // PropertyValueBuffer; primitives never do, matching stock defaults.
        cob.lconst_0().lstore(nullMaskSlot);
        for (int i = 0; i < props.size(); i++) {
            if (props.get(i).type().isPrimitive()) {
                continue;
            }
            Label nonNull = cob.newLabel();
            cob.aload(componentSlot[i]);
            cob.ifnonnull(nonNull);
            cob.lload(nullMaskSlot);
            cob.loadConstant(1L << i);
            cob.lor();
            cob.lstore(nullMaskSlot);
            cob.labelBinding(nonNull);
        }
        Label noNulls = cob.newLabel();
        cob.lload(nullMaskSlot);
        cob.lconst_0();
        cob.lcmp();
        cob.ifeq(noNulls);
        cob.aload(0).aload(ctxt).lload(nullMaskSlot);
        cob.loadConstant(props.size());
        cob.invokevirtual(CD_BASE, "_checkRecordNulls", MTD_CHECK_SEEN);
        cob.labelBinding(noNulls);
        ldcData(cob, "constructor", ConstantDescs.CD_MethodHandle);
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
        emitDelegateTail(cob, parser, ctxt, delegate);

        Label scopeEnd = cob.newBoundLabel();
        cob.localVariable(parser, "p", CD_JSON_PARSER, scopeStart, scopeEnd);
        cob.localVariable(ctxt, "ctxt", CD_DESER_CONTEXT, scopeStart, scopeEnd);
        for (int i = 0; i < props.size(); i++) {
            cob.localVariable(componentSlot[i], props.get(i).name(),
                    props.get(i).type().describeConstable().orElseThrow(),
                    scopeStart, scopeEnd);
        }
        cob.localVariable(matcherSlot, "matcher", CD_NAME_MATCHER, scopeStart, scopeEnd);
        cob.localVariable(ixSlot, "ix", ConstantDescs.CD_int, scopeStart, scopeEnd);
        cob.localVariable(seenSlot, "seen", ConstantDescs.CD_long, scopeStart, scopeEnd);
        cob.localVariable(propSlot, "prop", CD_SETTABLE_PROP, scopeStart, scopeEnd);
        cob.localVariable(excSlot, "e", CD_EXCEPTION, scopeStart, scopeEnd);
        if (views == ViewStrategy.MASK) {
            cob.localVariable(maskSlot, "viewMask", ConstantDescs.CD_long, scopeStart, scopeEnd);
        }
    }

    private static void emitRecordScalar(CodeBuilder cob, int parser, int ctxt, int slot,
            Class<?> type, String getter, MethodTypeDesc getterType, String stockName,
            String expectedToken) {
        Label useStock = cob.newLabel();
        Label done = cob.newLabel();
        emitExpectedTokenCheck(cob, parser, expectedToken, useStock);
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, getter, getterType);
        storeLocal(cob, type, slot);
        cob.goto_(done);
        cob.labelBinding(useStock);
        emitStockValueToLocal(cob, parser, ctxt, slot, type, stockName);
        cob.labelBinding(done);
    }

    // Reads the whole value through the stock property (parser positioned on
    // the value token), then converts the boxed result to the component type.
    private static void emitStockValueToLocal(CodeBuilder cob, int parser, int ctxt, int slot,
            Class<?> type, String stockName) {
        ldcData(cob, stockName, CD_SETTABLE_PROP);
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
            ClassDesc valueDesc, String stockName, String setterName, String expectedToken) {
        Label useStock = cob.newLabel();
        Label done = cob.newLabel();
        emitExpectedTokenCheck(cob, parser, expectedToken, useStock);
        if (setterName != null) {
            ldcData(cob, setterName, ConstantDescs.CD_MethodHandle);
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
        emitStockSet(cob, parser, ctxt, beanSlot, stockName);
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

    private static void emitChildCall(CodeBuilder cob, int parser, int ctxt, String childName) {
        ldcData(cob, childName, CD_BASE);
        cob.aload(parser).aload(ctxt);
        cob.invokevirtual(CD_BASE, "deserialize", MTD_DESERIALIZE);
    }

    private static void emitStockSet(CodeBuilder cob, int parser, int ctxt, int beanSlot,
            String stockName) {
        ldcData(cob, stockName, CD_SETTABLE_PROP);
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

    // Guards the generated loop's entry: anything the loop does not model - a
    // stream positioned on neither START_OBJECT nor PROPERTY_NAME (the
    // AsProperty polymorphic path hands subtype deserializers a stream on the
    // property after the type id) and, under the DELEGATE strategy only, an
    // active view - branches to the returned label, whose stock-delegation
    // tail the caller emits at the end of the method so the main body
    // decompiles un-nested. The MASK strategy keeps view-active calls on the
    // generated path through the visibility bitmask.
    private static Label emitEntryGuard(CodeBuilder cob, int parser, int ctxt,
            ViewStrategy views) {
        Label delegate = cob.newLabel();
        Label entryOk = cob.newLabel();
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "currentToken", MTD_NEXT_TOKEN);
        cob.getstatic(CD_JSON_TOKEN, "START_OBJECT", CD_JSON_TOKEN);
        cob.if_acmpeq(entryOk);
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "currentToken", MTD_NEXT_TOKEN);
        cob.getstatic(CD_JSON_TOKEN, "PROPERTY_NAME", CD_JSON_TOKEN);
        cob.if_acmpne(delegate);
        cob.labelBinding(entryOk);
        if (views == ViewStrategy.DELEGATE) {
            cob.aload(ctxt).invokevirtual(CD_DESER_CONTEXT, "getActiveView",
                    MTD_GET_ACTIVE_VIEW);
            cob.ifnonnull(delegate);
        }
        return delegate;
    }

    private static void emitDelegateTail(CodeBuilder cob, int parser, int ctxt, Label delegate) {
        cob.labelBinding(delegate);
        cob.aload(0).getfield(CD_BASE, "_fallback", CD_BEAN_DESER_BASE);
        cob.aload(parser).aload(ctxt);
        cob.invokevirtual(CD_BEAN_DESER_BASE, "deserialize", MTD_DESERIALIZE);
        cob.areturn();
    }

    // Advances to the value token and branches to useStock unless it is the
    // token the inline read expects. Null, quoted scalars, and mismatched
    // shapes all take the stock property, which owns coercion and null
    // handling. expectedToken null selects the boolean pair.
    private static void emitExpectedTokenCheck(CodeBuilder cob, int parser,
            String expectedToken, Label useStock) {
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "currentToken", MTD_NEXT_TOKEN);
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
    private static Label beginArm(CodeBuilder cob, String stockName, int propSlot, Label handler) {
        ldcData(cob, stockName, CD_SETTABLE_PROP);
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
                .invokevirtual(CD_JSON_PARSER, "nextNameMatchAndToken", MTD_NEXT_NAME_MATCH)
                .istore(ixSlot);
    }

    // First match of the loop. The entry guard admits START_OBJECT and
    // PROPERTY_NAME; a name entry matches the CURRENT name, mirroring stock
    // BeanDeserializer's currentNameMatch loop head. The fused arms consume
    // the current token, so a matched name entry advances to its value here;
    // fusing the entry itself would double-advance (the current name is
    // already consumed). Negative results stay on the name, as the unknown
    // arm expects.
    private static void emitFirstMatch(CodeBuilder cob, int parser, int matcherSlot, int ixSlot) {
        Label nameEntry = cob.newLabel();
        Label done = cob.newLabel();
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "currentToken", MTD_NEXT_TOKEN);
        cob.getstatic(CD_JSON_TOKEN, "PROPERTY_NAME", CD_JSON_TOKEN);
        cob.if_acmpeq(nameEntry);
        nextNameMatch(cob, parser, matcherSlot, ixSlot);
        cob.goto_(done);
        cob.labelBinding(nameEntry);
        cob.aload(parser).aload(matcherSlot)
                .invokevirtual(CD_JSON_PARSER, "currentNameMatch", MTD_NEXT_NAME_MATCH)
                .istore(ixSlot);
        cob.iload(ixSlot).iflt(done);
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "nextToken", MTD_NEXT_TOKEN).pop();
        cob.labelBinding(done);
    }

    private static void throwIse(CodeBuilder cob, String message) {
        cob.new_(CD_ISE).dup().ldc(message)
                .invokespecial(CD_ISE, ConstantDescs.INIT_NAME,
                        MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String))
                .athrow();
    }
}
