package tools.jackson.module.blackbird.codegen;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.MethodParameterInfo;
import java.lang.classfile.attribute.MethodParametersAttribute;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.deser.NullValueProvider;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.UnresolvedForwardReference;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueInstantiator;
import tools.jackson.databind.deser.bean.BeanDeserializer;
import tools.jackson.databind.deser.bean.BeanPropertyMap;
import tools.jackson.databind.deser.bean.BuilderBasedDeserializer;
import tools.jackson.databind.deser.impl.NullsConstantProvider;
import tools.jackson.databind.util.ClassUtil;
import tools.jackson.module.blackbird.codegen.ReaderCodegen.GenProp;
import tools.jackson.module.blackbird.codegen.ReaderCodegen.Kind;
import tools.jackson.module.blackbird.codegen.ReaderCodegen.Mode;
import tools.jackson.module.blackbird.codegen.ReaderCodegen.ViewStrategy;
import tools.jackson.module.blackbird.internal.GeneratedBuilderBase;
import tools.jackson.module.blackbird.internal.GeneratedReaderBase;

/**
 * Emits the class file of a hidden-class deserializer for one bean (see
 * {@link ReaderCodegen}, which defines it): a loop on nextNameMatch, a
 * tableswitch on the property index, inlined scalar reads for eligible
 * properties, and the stock SettableBeanProperty (a named classData constant)
 * for everything else. Compiled at release 25 into META-INF/versions/25,
 * since it uses the ClassFile API; reached through {@link ClassFileEmitters}.
 */
final class BeanReaderGenerator
{
    private static final ClassDesc CD_JSON_PARSER = Descs.of(JsonParser.class);
    private static final ClassDesc CD_JSON_TOKEN = Descs.of(JsonToken.class);
    private static final ClassDesc CD_DESER_CONTEXT = Descs.of(DeserializationContext.class);
    private static final ClassDesc CD_DESER_FEATURE = Descs.of(DeserializationFeature.class);
    private static final ClassDesc CD_NAME_MATCHER = Descs.of(PropertyNameMatcher.class);
    private static final ClassDesc CD_SETTABLE_PROP = Descs.of(SettableBeanProperty.class);
    private static final ClassDesc CD_NULL_PROVIDER = Descs.of(NullValueProvider.class);
    private static final ClassDesc CD_FORWARD_REF = Descs.of(UnresolvedForwardReference.class);
    private static final ClassDesc CD_NULLS_CONSTANT = Descs.of(NullsConstantProvider.class);
    private static final ClassDesc CD_READER_BASE = Descs.of(GeneratedReaderBase.class);
    private static final ClassDesc CD_BUILDER_BASE = Descs.of(GeneratedBuilderBase.class);
    private static final ClassDesc CD_VALUE_DESERIALIZER = Descs.of(ValueDeserializer.class);
    private static final ClassDesc CD_BEAN_PROPERTY_MAP = Descs.of(BeanPropertyMap.class);
    private static final ClassDesc CD_VALUE_INSTANTIATOR = Descs.of(ValueInstantiator.class);
    private static final ClassDesc CD_ISE = Descs.of(IllegalStateException.class);
    private static final ClassDesc CD_RTE = Descs.of(RuntimeException.class);
    private static final ClassDesc CD_EXCEPTION = ConstantDescs.CD_Exception;
    private static final ClassDesc CD_THROWABLE = ConstantDescs.CD_Throwable;
    private static final ClassDesc CD_OBJECT = ConstantDescs.CD_Object;

    private static final MethodTypeDesc MTD_READER_CTOR = MethodTypeDesc.of(
            ConstantDescs.CD_void, Descs.of(BeanDeserializer.class), CD_BEAN_PROPERTY_MAP,
            ConstantDescs.CD_String);
    private static final MethodTypeDesc MTD_BUILDER_CTOR = MethodTypeDesc.of(
            ConstantDescs.CD_void, Descs.of(BuilderBasedDeserializer.class), CD_BEAN_PROPERTY_MAP,
            ConstantDescs.CD_String);
    private static final MethodTypeDesc MTD_UNEXPECTED = MethodTypeDesc.of(CD_OBJECT,
            CD_JSON_PARSER, CD_DESER_CONTEXT, CD_OBJECT);
    private static final MethodTypeDesc MTD_NEXT_NAME_MATCH =
            MethodTypeDesc.of(ConstantDescs.CD_int, CD_NAME_MATCHER);
    private static final MethodTypeDesc MTD_NEXT_TOKEN = MethodTypeDesc.of(CD_JSON_TOKEN);
    private static final MethodTypeDesc MTD_GET_STRING = MethodTypeDesc.of(ConstantDescs.CD_String);
    private static final MethodTypeDesc MTD_GET_INT = MethodTypeDesc.of(ConstantDescs.CD_int);
    private static final MethodTypeDesc MTD_GET_LONG = MethodTypeDesc.of(ConstantDescs.CD_long);
    private static final MethodTypeDesc MTD_GET_BOOLEAN = MethodTypeDesc.of(ConstantDescs.CD_boolean);
    private static final MethodTypeDesc MTD_ASSIGN_CURRENT =
            MethodTypeDesc.of(ConstantDescs.CD_void, CD_OBJECT);
    private static final MethodTypeDesc MTD_DESERIALIZE_AND_SET = MethodTypeDesc.of(
            ConstantDescs.CD_void, CD_JSON_PARSER, CD_DESER_CONTEXT, CD_OBJECT);
    private static final MethodTypeDesc MTD_DESERIALIZE =
            MethodTypeDesc.of(CD_OBJECT, CD_JSON_PARSER, CD_DESER_CONTEXT);
    private static final MethodTypeDesc MTD_DESER_SET_RETURN = MethodTypeDesc.of(
            CD_OBJECT, CD_JSON_PARSER, CD_DESER_CONTEXT, CD_OBJECT);
    private static final MethodTypeDesc MTD_CREATE_DEFAULT =
            MethodTypeDesc.of(CD_OBJECT, CD_DESER_CONTEXT);
    private static final MethodTypeDesc MTD_NEW_INSTANCE = MethodTypeDesc.of(CD_OBJECT);
    private static final MethodTypeDesc MTD_GET_NULL_PROVIDER = MethodTypeDesc.of(CD_NULL_PROVIDER);
    private static final MethodTypeDesc MTD_GET_NULL_VALUE = MethodTypeDesc.of(CD_OBJECT,
            CD_DESER_CONTEXT);
    private static final MethodTypeDesc MTD_IS_SKIPPER = MethodTypeDesc.of(
            ConstantDescs.CD_boolean, CD_NULL_PROVIDER);
    private static final MethodTypeDesc MTD_BUILD_INVOKE = MethodTypeDesc.of(CD_OBJECT, CD_OBJECT);
    private static final MethodTypeDesc MTD_IS_BUILDER =
            MethodTypeDesc.of(ConstantDescs.CD_boolean, CD_OBJECT);
    private static final MethodTypeDesc MTD_FINISH_BUILD =
            MethodTypeDesc.of(CD_OBJECT, CD_DESER_CONTEXT, CD_OBJECT);
    private static final MethodTypeDesc MTD_INJECT = MethodTypeDesc.of(
            ConstantDescs.CD_void, CD_DESER_CONTEXT, CD_OBJECT);
    private static final MethodTypeDesc MTD_HANDLE_UNKNOWN = MethodTypeDesc.of(
            ConstantDescs.CD_void, CD_JSON_PARSER, CD_DESER_CONTEXT, CD_OBJECT);
    private static final MethodTypeDesc MTD_RECORD_UNKNOWN = MethodTypeDesc.of(
            CD_OBJECT, CD_JSON_PARSER, CD_DESER_CONTEXT, CD_OBJECT);
    private static final MethodTypeDesc MTD_RECORD_FINISH = MethodTypeDesc.of(
            CD_OBJECT, CD_DESER_CONTEXT, CD_OBJECT, CD_OBJECT, CD_OBJECT);
    private static final MethodTypeDesc MTD_RECORD_BUILD = MethodTypeDesc.of(CD_OBJECT,
            CD_JSON_PARSER, CD_DESER_CONTEXT, ConstantDescs.CD_long,
            CD_OBJECT.arrayType(), CD_THROWABLE);
    private static final MethodTypeDesc MTD_RECORD_FORWARD_REF = MethodTypeDesc.of(CD_OBJECT,
            CD_DESER_CONTEXT, CD_SETTABLE_PROP, CD_FORWARD_REF, CD_OBJECT);
    private static final MethodTypeDesc MTD_CREATION_FAILURE =
            MethodTypeDesc.of(CD_OBJECT, CD_DESER_CONTEXT, CD_THROWABLE);
    private static final MethodTypeDesc MTD_PROP_WRAP = MethodTypeDesc.of(
            CD_RTE, CD_THROWABLE, CD_OBJECT, CD_SETTABLE_PROP, CD_DESER_CONTEXT);
    private static final MethodTypeDesc MTD_STORE_FAILURE = MethodTypeDesc.of(CD_RTE,
            CD_THROWABLE, CD_OBJECT, CD_OBJECT, CD_SETTABLE_PROP, CD_JSON_PARSER,
            CD_DESER_CONTEXT);
    // Builder codecs: the same with the input name and the entry kind (see
    // GeneratedBuilderBase._failedName) before the DeserializationContext.
    private static final MethodTypeDesc MTD_BUILDER_PROP_WRAP = MethodTypeDesc.of(
            CD_RTE, CD_THROWABLE, CD_OBJECT, CD_SETTABLE_PROP, ConstantDescs.CD_String,
            ConstantDescs.CD_boolean, CD_DESER_CONTEXT);
    private static final MethodTypeDesc MTD_BUILDER_STORE_FAILURE = MethodTypeDesc.of(CD_RTE,
            CD_THROWABLE, CD_OBJECT, CD_OBJECT, CD_SETTABLE_PROP, CD_JSON_PARSER,
            ConstantDescs.CD_String, ConstantDescs.CD_boolean, CD_DESER_CONTEXT);
    private static final MethodTypeDesc MTD_IS_ENABLED =
            MethodTypeDesc.of(ConstantDescs.CD_boolean, CD_DESER_FEATURE);
    private static final MethodTypeDesc MTD_GET_ACTIVE_VIEW =
            MethodTypeDesc.of(ConstantDescs.CD_Class);
    private static final MethodTypeDesc MTD_VIEW_MASK =
            MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_Class);
    private static final MethodTypeDesc MTD_HIDDEN_VIEW = MethodTypeDesc.of(
            ConstantDescs.CD_void, CD_JSON_PARSER, CD_DESER_CONTEXT, CD_SETTABLE_PROP);
    private static final MethodTypeDesc MTD_VISIBLE_IN_VIEW =
            MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_Class);

    // Stack map frames can need the supertypes of the types the code uses.
    // The default resolver reads class files through the system class
    // loader, which does not see Jackson in an application server, an OSGi
    // container or a fat jar. The lookup of this module sees it.
    static final ClassFile CLASS_FILE = ClassFile.of(
            ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.defaultResolver()
                    .orElse(ClassHierarchyResolver.ofClassLoading(MethodHandles.lookup()))));

    private BeanReaderGenerator() {}

    // Builder codecs extend GeneratedBuilderBase, all others
    // GeneratedReaderBase; both declare the helpers the generated code calls.
    private static ClassDesc baseOf(ReaderCodegen.Plan plan) {
        return (plan.mode() == Mode.BUILDER) ? CD_BUILDER_BASE : CD_READER_BASE;
    }

    static byte[] emit(ReaderCodegen.Plan plan) {
        return withinSizeLimit(build(plan));
    }

    // The generated class, or null when one of its methods is too large for
    // the JIT (see ClassFileEmitters.MAX_METHOD_BYTES); measured on the
    // emitted bytes rather than estimated.
    static byte[] withinSizeLimit(byte[] bytes) {
        for (MethodModel method : ClassFile.of().parse(bytes).methods()) {
            Optional<CodeAttribute> code = method.findAttribute(Attributes.code());
            if (code.isPresent()
                    && code.get().codeLength() > ClassFileEmitters.MAX_METHOD_BYTES) {
                return null;
            }
        }
        return bytes;
    }

    private static byte[] build(ReaderCodegen.Plan plan) {
        ClassDesc thisClass = ClassDesc.of(plan.className());
        ClassDesc base = baseOf(plan);
        MethodTypeDesc ctor = (plan.mode() == Mode.BUILDER) ? MTD_BUILDER_CTOR : MTD_READER_CTOR;
        return CLASS_FILE.build(thisClass, clb -> {
            clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            clb.withSuperclass(base);
            clb.withMethod(ConstantDescs.INIT_NAME, ctor, ClassFile.ACC_PUBLIC,
                    mb -> mb.with(params("src", "beanProperties", "layout"))
                            .withCode(cob -> cob.aload(0).aload(1).aload(2).aload(3)
                                    .invokespecial(base, ConstantDescs.INIT_NAME, ctor)
                                    .return_()));
            clb.withMethod("deserialize", MTD_DESERIALIZE, ClassFile.ACC_PUBLIC,
                    mb -> mb.with(params("p", "ctxt")).withCode(cob -> {
                        if (plan.mode() == Mode.RECORD) {
                            buildRecordDeserialize(cob, plan);
                        } else {
                            buildDeserialize(cob, plan);
                        }
                    }));
            if (plan.views() == ViewStrategy.MASK) {
                emitComputeViewMask(clb, plan.props(), plan.stockName());
            }
        });
    }

    static void ldcData(CodeBuilder cob, String name, ClassDesc type) {
        cob.ldc(Descs.classDataEntry(name, type));
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
    private static void emitViewMask(CodeBuilder cob, ClassDesc base, int ctxt, int maskSlot) {
        Label nullView = cob.newLabel();
        Label haveMask = cob.newLabel();
        cob.aload(ctxt).invokevirtual(CD_DESER_CONTEXT, "getActiveView", MTD_GET_ACTIVE_VIEW);
        cob.dup();
        cob.ifnull(nullView);
        cob.aload(0);
        cob.swap();
        cob.invokevirtual(base, "_viewMask", MTD_VIEW_MASK);
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
    private static void emitArmViewCheck(CodeBuilder cob, ClassDesc base, int parser,
            int ctxt, int maskSlot,
            int matcherSlot, int ixSlot, int armIndex, String stockName, Label loop) {
        Label visible = cob.newLabel();
        cob.lload(maskSlot);
        cob.loadConstant(1L << armIndex);
        cob.land();
        cob.lconst_0();
        cob.lcmp();
        cob.ifne(visible);
        // The fused match already advanced to the value token, so _hiddenView
        // consumes the current value without advancing.
        cob.aload(0).aload(parser).aload(ctxt);
        ldcData(cob, stockName, CD_SETTABLE_PROP);
        cob.invokevirtual(base, "_hiddenView", MTD_HIDDEN_VIEW);
        nextNameMatch(cob, parser, matcherSlot, ixSlot);
        cob.goto_(loop);
        cob.labelBinding(visible);
    }

    // The loop head shared by all modes: dispatch on the match index (arms,
    // end of object, unknown name, anything else) with a tableswitch that
    // sends each matcher index to its arm. Returns the case label per arm,
    // null for an arm no name reaches (its code is not emitted).
    private static Label[] emitLoopHead(CodeBuilder cob, ReaderCodegen.Plan plan, int ixSlot,
            Label loop, Label endObject, Label unknown, Label oddToken, Label defaultCase) {
        List<GenProp> props = plan.props();
        int[] matchArms = plan.matchArms();
        Label switchPart = cob.newLabel();
        cob.labelBinding(loop);
        cob.iload(ixSlot).ifge(switchPart);
        cob.iload(ixSlot).iconst_m1().if_icmpeq(endObject);
        cob.iload(ixSlot).ldc(-2).if_icmpeq(unknown);
        cob.goto_(oddToken);

        cob.labelBinding(switchPart);
        Label[] caseLabels = new Label[props.size()];
        List<SwitchCase> cases = new ArrayList<>(matchArms.length);
        for (int k = 0; k < matchArms.length; k++) {
            int arm = matchArms[k];
            if (caseLabels[arm] == null) {
                caseLabels[arm] = cob.newLabel();
            }
            cases.add(SwitchCase.of(k, caseLabels[arm]));
        }
        cob.iload(ixSlot);
        if (cases.isEmpty()) {
            cob.pop();
            cob.goto_(defaultCase);
        } else {
            cob.tableswitch(0, matchArms.length - 1, defaultCase, cases);
        }
        return caseLabels;
    }

    // POJO and builder modes. The receiver local holds the bean, or the
    // builder whose setters may return a replacement builder (kept, like
    // stock deserializeSetAndReturn); the build method ends builder mode.
    private static void buildDeserialize(CodeBuilder cob, ReaderCodegen.Plan plan) {
        final boolean builderMode = plan.mode() == Mode.BUILDER;
        final ClassDesc base = baseOf(plan);
        final List<GenProp> props = plan.props();
        final ViewStrategy views = plan.views();
        final int parser = 1;
        final int ctxt = 2;
        final int beanSlot = 3;
        final int matcherSlot = 4;
        final int ixSlot = 5;
        final int propSlot = 6;
        final int excSlot = 7;
        final int maskSlot = 8;
        final int valueSlot = 10;
        // Builder mode with names that can differ from the property's own
        // (see GeneratedBuilderBase._failedName): each arm reads the input
        // name, and the first match records the entry token.
        final boolean inputNames = builderMode && plan.inputNames();
        final int inputNameSlot = 11;
        final int startObjectSlot = 12;
        final Names names = !builderMode ? null
                : inputNames ? new Names(inputNameSlot, startObjectSlot) : Names.NONE;
        Label propHandler = cob.newLabel();
        Label storeHandler = cob.newLabel();

        // A failing setter or field store gets the stock property's message,
        // so each generated store has its own exception region. The regions
        // nest inside the arm regions, and the JVM takes the first matching
        // exception-table entry, so they register before any arm region.
        Label[] storeStart = new Label[props.size()];
        Label[] storeEnd = new Label[props.size()];
        boolean anyStore = false;
        for (int i = 0; i < props.size(); i++) {
            if (props.get(i).handle() != null) {
                storeStart[i] = cob.newLabel();
                storeEnd[i] = cob.newLabel();
                cob.exceptionCatch(storeStart[i], storeEnd[i], storeHandler, CD_EXCEPTION);
                anyStore = true;
            }
        }

        Label scopeStart = cob.newBoundLabel();
        Label delegate = emitEntryGuard(cob, parser, ctxt, views);
        if (views == ViewStrategy.MASK) {
            emitViewMask(cob, base, ctxt, maskSlot);
        }
        if (anyStore) {
            // Read by the store handler; child arms set it before storing.
            cob.aconst_null().astore(valueSlot);
        }

        Label ctorHandler = null;
        Label haveBean = null;
        if (plan.mode() == Mode.CONSTRUCTOR) {
            ctorHandler = cob.newLabel();
            haveBean = cob.newLabel();
            Label ctorStart = cob.newLabel();
            Label ctorEnd = cob.newLabel();
            cob.exceptionCatch(ctorStart, ctorEnd, ctorHandler, CD_EXCEPTION);
            cob.labelBinding(ctorStart);
            ldcData(cob, "constructor", ConstantDescs.CD_MethodHandle);
            cob.invokevirtual(ConstantDescs.CD_MethodHandle, "invokeExact", MTD_NEW_INSTANCE);
            cob.labelBinding(ctorEnd);
            cob.labelBinding(haveBean);
        } else {
            ldcData(cob, "instantiator", CD_VALUE_INSTANTIATOR);
            cob.aload(ctxt);
            cob.invokevirtual(CD_VALUE_INSTANTIATOR, "createUsingDefault", MTD_CREATE_DEFAULT);
        }
        cob.astore(beanSlot);
        if (!builderMode) {
            cob.aload(parser).aload(beanSlot)
               .invokevirtual(CD_JSON_PARSER, "assignCurrentValue", MTD_ASSIGN_CURRENT);
        }
        if (plan.injectables()) {
            cob.aload(0).aload(ctxt).aload(beanSlot)
               .invokevirtual(base, "_injectValues", MTD_INJECT);
        }

        ldcData(cob, "matcher", CD_NAME_MATCHER);
        cob.astore(matcherSlot);

        Label loop = cob.newLabel();
        Label endObject = cob.newLabel();
        Label unknown = cob.newLabel();
        Label oddToken = cob.newLabel();
        Label defaultCase = cob.newLabel();

        emitFirstMatch(cob, parser, matcherSlot, ixSlot, inputNames ? startObjectSlot : -1);
        Label[] caseLabels = emitLoopHead(cob, plan, ixSlot, loop, endObject, unknown,
                oddToken, defaultCase);

        for (int i = 0; i < props.size(); i++) {
            cob.labelBinding(caseLabels[i]);
            if (views == ViewStrategy.MASK) {
                emitArmViewCheck(cob, base, parser, ctxt, maskSlot, matcherSlot, ixSlot, i,
                        plan.stockName()[i], loop);
            }
            if (inputNames) {
                // As stock's general loop: the name of the value token.
                cob.aload(parser).invokevirtual(CD_JSON_PARSER, "currentName", MTD_GET_STRING);
                cob.astore(inputNameSlot);
            }
            Label armEnd = beginArm(cob, plan.stockName()[i], propSlot, propHandler);
            GenProp prop = props.get(i);
            Store store = (prop.handle() == null) ? null
                    : new Store(plan.handleName()[i], storeStart[i], storeEnd[i], builderMode);
            String stockName = plan.stockName()[i];
            switch (prop.kind()) {
                case STRING -> emitScalar(cob, beanSlot, parser, ctxt, "getString",
                        MTD_GET_STRING, CD_OBJECT, stockName, store, "VALUE_STRING");
                case INT -> emitScalar(cob, beanSlot, parser, ctxt, "getIntValue",
                        MTD_GET_INT, ConstantDescs.CD_int, stockName, store, "VALUE_NUMBER_INT");
                case LONG -> emitScalar(cob, beanSlot, parser, ctxt, "getLongValue",
                        MTD_GET_LONG, ConstantDescs.CD_long, stockName, store, "VALUE_NUMBER_INT");
                case BOOLEAN -> emitScalar(cob, beanSlot, parser, ctxt, "getBooleanValue",
                        MTD_GET_BOOLEAN, ConstantDescs.CD_boolean, stockName, store, null);
                case CHILD -> emitChildArm(cob, beanSlot, parser, ctxt, valueSlot,
                        plan.childName()[i], stockName, store);
                case STOCK ->
                    // The fused match left the value token current; the stock
                    // property consumes it.
                    emitStockSet(cob, parser, ctxt, beanSlot, stockName, builderMode);
            }
            cob.labelBinding(armEnd);
            nextNameMatch(cob, parser, matcherSlot, ixSlot);
            cob.goto_(loop);
        }

        cob.labelBinding(defaultCase);
        throwIse(cob, "bad property index");

        cob.labelBinding(endObject);
        if (builderMode) {
            emitBuild(cob, base, ctxt, beanSlot, excSlot);
        } else {
            cob.aload(beanSlot).areturn();
        }

        cob.labelBinding(unknown);
        cob.aload(0).aload(parser).aload(ctxt).aload(beanSlot);
        cob.invokevirtual(base, "_handleUnknown", MTD_HANDLE_UNKNOWN);
        nextNameMatch(cob, parser, matcherSlot, ixSlot);
        cob.goto_(loop);

        cob.labelBinding(oddToken);
        cob.aload(0).aload(parser).aload(ctxt).aload(beanSlot)
           .invokevirtual(base, "_unexpectedToken", MTD_UNEXPECTED)
           .areturn();

        emitPropertyHandler(cob, base, propHandler, ctxt, propSlot, excSlot, beanSlot, names);
        if (anyStore) {
            cob.labelBinding(storeHandler);
            cob.astore(excSlot);
            cob.aload(0).aload(excSlot).aload(beanSlot).aload(valueSlot).aload(propSlot)
               .aload(parser);
            if (builderMode) {
                names.load(cob);
                cob.aload(ctxt);
                cob.invokevirtual(base, "_storeFailure", MTD_BUILDER_STORE_FAILURE);
            } else {
                cob.aload(ctxt);
                cob.invokevirtual(base, "_storeFailure", MTD_STORE_FAILURE);
            }
            cob.athrow();
        }
        if (ctorHandler != null) {
            // StdValueInstantiator.createUsingDefault semantics; a problem
            // handler's replacement value continues as the bean.
            cob.labelBinding(ctorHandler);
            cob.astore(excSlot);
            cob.aload(0).aload(ctxt).aload(excSlot);
            cob.invokevirtual(base, "_instantiationFailure", MTD_CREATION_FAILURE);
            cob.goto_(haveBean);
        }
        emitDelegateTail(cob, base, parser, ctxt, delegate);

        Label scopeEnd = cob.newBoundLabel();
        cob.localVariable(parser, "p", CD_JSON_PARSER, scopeStart, scopeEnd);
        cob.localVariable(ctxt, "ctxt", CD_DESER_CONTEXT, scopeStart, scopeEnd);
        cob.localVariable(beanSlot, builderMode ? "builder" : "bean", CD_OBJECT,
                scopeStart, scopeEnd);
        cob.localVariable(matcherSlot, "matcher", CD_NAME_MATCHER, scopeStart, scopeEnd);
        cob.localVariable(ixSlot, "ix", ConstantDescs.CD_int, scopeStart, scopeEnd);
        cob.localVariable(propSlot, "prop", CD_SETTABLE_PROP, scopeStart, scopeEnd);
        cob.localVariable(excSlot, "e", CD_THROWABLE, scopeStart, scopeEnd);
        if (views == ViewStrategy.MASK) {
            cob.localVariable(maskSlot, "viewMask", ConstantDescs.CD_long, scopeStart, scopeEnd);
        }
        if (anyStore) {
            cob.localVariable(valueSlot, "value", CD_OBJECT, scopeStart, scopeEnd);
        }
        if (inputNames) {
            cob.localVariable(inputNameSlot, "inputName", ConstantDescs.CD_String,
                    scopeStart, scopeEnd);
            cob.localVariable(startObjectSlot, "startObject", ConstantDescs.CD_boolean,
                    scopeStart, scopeEnd);
        }
    }

    // BuilderBasedDeserializer.finishBuild: a failing build method goes
    // through wrapInstantiationProblem. A receiver that is not a builder
    // instance (see GeneratedBuilderBase._isBuilder) takes the stock call.
    private static void emitBuild(CodeBuilder cob, ClassDesc base, int ctxt, int builderSlot,
            int excSlot) {
        Label start = cob.newLabel();
        Label end = cob.newLabel();
        Label handler = cob.newLabel();
        Label notBuilder = cob.newLabel();
        cob.aload(0).aload(builderSlot);
        cob.invokevirtual(base, "_isBuilder", MTD_IS_BUILDER);
        cob.ifeq(notBuilder);
        cob.exceptionCatch(start, end, handler, CD_EXCEPTION);
        cob.labelBinding(start);
        ldcData(cob, "buildMethod", ConstantDescs.CD_MethodHandle);
        cob.aload(builderSlot);
        cob.invokevirtual(ConstantDescs.CD_MethodHandle, "invokeExact", MTD_BUILD_INVOKE);
        cob.labelBinding(end);
        cob.areturn();
        cob.labelBinding(handler);
        cob.astore(excSlot);
        cob.aload(0).aload(ctxt).aload(excSlot);
        cob.invokevirtual(base, "_buildFailure", MTD_CREATION_FAILURE);
        cob.areturn();
        cob.labelBinding(notBuilder);
        cob.aload(0).aload(ctxt).aload(builderSlot);
        cob.invokevirtual(base, "_finishBuild", MTD_FINISH_BUILD);
        cob.areturn();
    }

    // A generated store: the class-data name of its (Object, eV)void handle
    // ((Object, eV)Object in builder mode, whose non-null result becomes the
    // new receiver) and the labels of its exception region.
    private record Store(String handleName, Label start, Label end, boolean builderMode) {}

    private static void emitStore(CodeBuilder cob, Store store, int receiverSlot,
            ClassDesc erasedDesc) {
        cob.labelBinding(store.start());
        if (store.builderMode()) {
            cob.invokevirtual(ConstantDescs.CD_MethodHandle, "invokeExact", MethodTypeDesc.of(
                    CD_OBJECT, CD_OBJECT, erasedDesc));
            cob.labelBinding(store.end());
            // Stock deserializeSetAndReturn keeps the builder when a fluent
            // setter returns null.
            Label replace = cob.newLabel();
            cob.dup();
            cob.ifnonnull(replace);
            cob.pop();
            cob.aload(receiverSlot);
            cob.labelBinding(replace);
            cob.astore(receiverSlot);
        } else {
            cob.invokevirtual(ConstantDescs.CD_MethodHandle, "invokeExact", MethodTypeDesc.of(
                    ConstantDescs.CD_void, CD_OBJECT, erasedDesc));
            cob.labelBinding(store.end());
        }
    }

    // The null branch (and any other unexpected token) enters the stock
    // property with the parser on that token, which is the position its
    // deserializeAndSet expects. Without a generated store (the property
    // keeps its stock arm whatever the token), only the stock call remains.
    private static void emitScalar(CodeBuilder cob, int receiverSlot, int parser, int ctxt,
            String getter, MethodTypeDesc getterType, ClassDesc erasedDesc,
            String stockName, Store store, String expectedToken) {
        boolean builderMode = (store != null) && store.builderMode();
        Label useStock = cob.newLabel();
        Label done = cob.newLabel();
        emitExpectedTokenCheck(cob, parser, expectedToken, useStock);
        ldcData(cob, store.handleName(), ConstantDescs.CD_MethodHandle);
        cob.aload(receiverSlot);
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, getter, getterType);
        emitStore(cob, store, receiverSlot, erasedDesc);
        cob.goto_(done);
        cob.labelBinding(useStock);
        emitStockSet(cob, parser, ctxt, receiverSlot, stockName, builderMode);
        cob.labelBinding(done);
    }

    // A child bean on START_OBJECT goes to the linked child codec, anything
    // else to the stock property. The child value passes through valueSlot so
    // that a failing store can name its class, as stock does. A null from the
    // child codec (a null build result, or a problem handler's) gets what
    // stock deserializeAndSet gives it ([databind#2023]): skipped for a
    // skipping null provider, otherwise replaced by the provider's value.
    private static void emitChildArm(CodeBuilder cob, int receiverSlot, int parser, int ctxt,
            int valueSlot, String childName, String stockName, Store store) {
        Label childStock = cob.newLabel();
        Label childDone = cob.newLabel();
        Label haveValue = cob.newLabel();
        // The fused match left the value token current.
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "currentToken", MTD_NEXT_TOKEN);
        cob.getstatic(CD_JSON_TOKEN, "START_OBJECT", CD_JSON_TOKEN);
        cob.if_acmpne(childStock);
        emitChildCall(cob, parser, ctxt, childName);
        cob.astore(valueSlot);
        cob.aload(valueSlot).ifnonnull(haveValue);
        emitNullProvider(cob, stockName);
        cob.invokestatic(CD_NULLS_CONSTANT, "isSkipper", MTD_IS_SKIPPER);
        cob.ifne(childDone);
        emitNullValue(cob, ctxt, stockName);
        cob.astore(valueSlot);
        cob.labelBinding(haveValue);
        ldcData(cob, store.handleName(), ConstantDescs.CD_MethodHandle);
        cob.aload(receiverSlot);
        cob.aload(valueSlot);
        emitStore(cob, store, receiverSlot, CD_OBJECT);
        cob.goto_(childDone);
        cob.labelBinding(childStock);
        emitStockSet(cob, parser, ctxt, receiverSlot, stockName, store.builderMode());
        cob.labelBinding(childDone);
    }

    private static void emitNullProvider(CodeBuilder cob, String stockName) {
        ldcData(cob, stockName, CD_SETTABLE_PROP);
        cob.invokevirtual(CD_SETTABLE_PROP, "getNullValueProvider", MTD_GET_NULL_PROVIDER);
    }

    // The stock property's null value (its null provider's, which applies
    // Nulls.FAIL, AS_EMPTY and the null config overrides).
    private static void emitNullValue(CodeBuilder cob, int ctxt, String stockName) {
        emitNullProvider(cob, stockName);
        cob.aload(ctxt);
        cob.invokeinterface(CD_NULL_PROVIDER, "getNullValue", MTD_GET_NULL_VALUE);
    }

    // Child codecs are reader or builder codecs; the constant receiver makes
    // the call monomorphic either way.
    private static void emitChildCall(CodeBuilder cob, int parser, int ctxt, String childName) {
        ldcData(cob, childName, CD_VALUE_DESERIALIZER);
        cob.aload(parser).aload(ctxt);
        cob.invokevirtual(CD_VALUE_DESERIALIZER, "deserialize", MTD_DESERIALIZE);
    }

    // The stock property reads and stores the value: deserializeAndSet, or
    // in builder mode deserializeSetAndReturn, whose result (a fluent
    // builder may return a replacement instance) becomes the new builder.
    private static void emitStockSet(CodeBuilder cob, int parser, int ctxt, int receiverSlot,
            String stockName, boolean builderMode) {
        ldcData(cob, stockName, CD_SETTABLE_PROP);
        cob.aload(parser).aload(ctxt).aload(receiverSlot);
        if (builderMode) {
            cob.invokevirtual(CD_SETTABLE_PROP, "deserializeSetAndReturn", MTD_DESER_SET_RETURN);
            cob.astore(receiverSlot);
        } else {
            cob.invokevirtual(CD_SETTABLE_PROP, "deserializeAndSet", MTD_DESERIALIZE_AND_SET);
        }
    }

    // Record mode: components collect into typed locals in canonical order.
    // At the end of the object the canonical constructor runs directly when
    // the document supplied every component and nothing needs stock
    // argument handling; otherwise, and when the constructor throws, the base
    // finishes through the stock creator (see GeneratedReaderBase
    // ._recordBuild). Names no component takes are handled, or buffered and
    // replayed against the record, the way the stock property-based loop
    // handles them.
    private static void buildRecordDeserialize(CodeBuilder cob, ReaderCodegen.Plan plan) {
        final ClassDesc base = CD_READER_BASE;
        final List<GenProp> props = plan.props();
        final ViewStrategy views = plan.views();
        final int n = props.size();
        final int parser = 1;
        final int ctxt = 2;

        int next = 3;
        int[] componentSlot = new int[n];
        boolean anyPrimitive = false;
        for (int i = 0; i < n; i++) {
            componentSlot[i] = next;
            Class<?> t = props.get(i).type();
            next += (t == long.class || t == double.class) ? 2 : 1;
            anyPrimitive |= t.isPrimitive();
        }
        final int matcherSlot = next++;
        final int ixSlot = next++;
        final int seenSlot = next;
        next += 2;
        final int rawPrimSlot = next;
        next += 2;
        final int maskSlot = next;
        next += 2;
        final int propSlot = next++;
        final int excSlot = next++;
        final int unknownSlot = next++;
        final int beanSlot = next++;
        final int pendingSlot = next++;
        final int referringsSlot = next++;
        // Primitive components: the stock value when it is not exactly the
        // wrapper (see emitStockValueToLocal).
        int[] rawSlot = new int[n];
        for (int i = 0; i < n; i++) {
            rawSlot[i] = props.get(i).type().isPrimitive() ? next++ : -1;
        }
        Label propHandler = cob.newLabel();

        Label scopeStart = cob.newBoundLabel();
        Label delegate = emitEntryGuard(cob, parser, ctxt, views);
        if (views == ViewStrategy.MASK) {
            emitViewMask(cob, base, ctxt, maskSlot);
        }

        for (int i = 0; i < n; i++) {
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
            if (rawSlot[i] >= 0) {
                cob.aconst_null().astore(rawSlot[i]);
            }
        }
        cob.lconst_0().lstore(seenSlot);
        cob.lconst_0().lstore(rawPrimSlot);
        cob.aconst_null().astore(unknownSlot);
        cob.aconst_null().astore(referringsSlot);

        ldcData(cob, "matcher", CD_NAME_MATCHER);
        cob.astore(matcherSlot);

        Label loop = cob.newLabel();
        Label endObject = cob.newLabel();
        Label unknown = cob.newLabel();
        Label oddToken = cob.newLabel();
        Label defaultCase = cob.newLabel();

        emitFirstMatch(cob, parser, matcherSlot, ixSlot, -1);
        Label[] caseLabels = emitLoopHead(cob, plan, ixSlot, loop, endObject, unknown,
                oddToken, defaultCase);

        for (int i = 0; i < n; i++) {
            if (caseLabels[i] == null) {
                continue;
            }
            cob.labelBinding(caseLabels[i]);
            if (views == ViewStrategy.MASK) {
                // A hidden component stays unseen, so the stock creator gives
                // it the absent value, as for a stock hidden creator property.
                emitArmViewCheck(cob, base, parser, ctxt, maskSlot, matcherSlot, ixSlot, i,
                        plan.stockName()[i], loop);
            }
            GenProp prop = props.get(i);
            // A stock component value may be a forward Object Id reference,
            // which the stock loop catches; the region registers before the
            // arm's, so that it takes precedence.
            Label fwdStart = null;
            Label fwdEnd = null;
            Label fwdHandler = null;
            if (prop.kind() == Kind.STOCK) {
                fwdStart = cob.newLabel();
                fwdEnd = cob.newLabel();
                fwdHandler = cob.newLabel();
                cob.exceptionCatch(fwdStart, fwdEnd, fwdHandler, CD_FORWARD_REF);
            }
            Label armEnd = beginArm(cob, plan.stockName()[i], propSlot, propHandler);
            Class<?> t = prop.type();
            String stockName = plan.stockName()[i];
            ComponentLocal local = new ComponentLocal(componentSlot[i], t, rawPrimSlot, rawSlot[i], i);
            switch (prop.kind()) {
                case STRING -> emitRecordScalar(cob, parser, ctxt, local,
                        "getString", MTD_GET_STRING, stockName, "VALUE_STRING");
                case INT -> emitRecordScalar(cob, parser, ctxt, local,
                        "getIntValue", MTD_GET_INT, stockName, "VALUE_NUMBER_INT");
                case LONG -> emitRecordScalar(cob, parser, ctxt, local,
                        "getLongValue", MTD_GET_LONG, stockName, "VALUE_NUMBER_INT");
                case BOOLEAN -> emitRecordScalar(cob, parser, ctxt, local,
                        "getBooleanValue", MTD_GET_BOOLEAN, stockName, null);
                case CHILD -> {
                    Label childStock = cob.newLabel();
                    Label childDone = cob.newLabel();
                    cob.aload(parser).invokevirtual(CD_JSON_PARSER, "currentToken", MTD_NEXT_TOKEN);
                    cob.getstatic(CD_JSON_TOKEN, "START_OBJECT", CD_JSON_TOKEN);
                    cob.if_acmpne(childStock);
                    emitChildCall(cob, parser, ctxt, plan.childName()[i]);
                    cob.astore(componentSlot[i]);
                    // A null child value: the null provider's value, as stock
                    // SettableBeanProperty.deserialize gives a creator property.
                    cob.aload(componentSlot[i]).ifnonnull(childDone);
                    emitNullValue(cob, ctxt, stockName);
                    cob.astore(componentSlot[i]);
                    cob.goto_(childDone);
                    cob.labelBinding(childStock);
                    emitStockValueToLocal(cob, parser, ctxt, local, stockName);
                    cob.labelBinding(childDone);
                }
                case STOCK -> {
                    cob.labelBinding(fwdStart);
                    emitStockValueToLocal(cob, parser, ctxt, local, stockName);
                    cob.labelBinding(fwdEnd);
                }
            }
            cob.labelBinding(armEnd);
            cob.lload(seenSlot);
            cob.loadConstant(1L << i);
            cob.lor();
            cob.lstore(seenSlot);
            nextNameMatch(cob, parser, matcherSlot, ixSlot);
            cob.goto_(loop);
            if (fwdHandler != null) {
                // The component is assigned null, as in stock.
                cob.labelBinding(fwdHandler);
                cob.astore(excSlot);
                cob.aload(0).aload(ctxt).aload(propSlot).aload(excSlot)
                   .checkcast(CD_FORWARD_REF).aload(referringsSlot);
                cob.invokevirtual(base, "_recordForwardRef", MTD_RECORD_FORWARD_REF);
                cob.astore(referringsSlot);
                if (t.isPrimitive()) {
                    cob.aconst_null().astore(rawSlot[i]);
                    emitRawPrimitive(cob, local, true);
                } else {
                    cob.aconst_null().astore(componentSlot[i]);
                }
                cob.goto_(armEnd);
            }
        }

        cob.labelBinding(defaultCase);
        throwIse(cob, "bad property index");

        cob.labelBinding(endObject);
        Label slow = cob.newLabel();
        Label finish = cob.newLabel();
        cob.aconst_null().astore(pendingSlot);
        long allSeen = (n == 64) ? -1L : (1L << n) - 1;
        cob.lload(seenSlot);
        cob.loadConstant(allSeen);
        cob.lcmp();
        cob.ifne(slow);
        if (anyPrimitive) {
            cob.lload(rawPrimSlot);
            cob.lconst_0();
            cob.lcmp();
            cob.ifne(slow);
        }
        cob.aload(ctxt);
        cob.getstatic(CD_DESER_FEATURE, "FAIL_ON_NULL_CREATOR_PROPERTIES", CD_DESER_FEATURE);
        cob.invokevirtual(CD_DESER_CONTEXT, "isEnabled", MTD_IS_ENABLED);
        cob.ifne(slow);
        // The constructor handle is erased ((e1..en)Object, see MemberHandles),
        // so the call descriptor uses Object for reference components.
        Label ctorStart = cob.newLabel();
        Label ctorEnd = cob.newLabel();
        Label ctorHandler = cob.newLabel();
        cob.exceptionCatch(ctorStart, ctorEnd, ctorHandler, CD_EXCEPTION);
        cob.labelBinding(ctorStart);
        ldcData(cob, "constructor", ConstantDescs.CD_MethodHandle);
        ClassDesc[] paramDescs = new ClassDesc[n];
        for (int i = 0; i < n; i++) {
            Class<?> t = props.get(i).type();
            paramDescs[i] = t.isPrimitive() ? Descs.of(t) : CD_OBJECT;
            loadLocal(cob, t, componentSlot[i]);
        }
        cob.invokevirtual(ConstantDescs.CD_MethodHandle, "invokeExact",
                MethodTypeDesc.of(CD_OBJECT, paramDescs));
        cob.labelBinding(ctorEnd);
        cob.astore(beanSlot);
        cob.goto_(finish);
        // The constructor failed with every component present: the base
        // reports it the way the stock instantiator would.
        cob.labelBinding(ctorHandler);
        cob.astore(pendingSlot);
        cob.labelBinding(slow);
        cob.aload(0).aload(parser).aload(ctxt).lload(seenSlot);
        cob.loadConstant(n);
        cob.anewarray(CD_OBJECT);
        for (int i = 0; i < n; i++) {
            Class<?> t = props.get(i).type();
            cob.dup();
            cob.loadConstant(i);
            if (t.isPrimitive()) {
                // A raw stock value goes to the stock creator as it is.
                Label boxIt = cob.newLabel();
                Label stored = cob.newLabel();
                cob.lload(rawPrimSlot).loadConstant(1L << i).land().lconst_0().lcmp();
                cob.ifeq(boxIt);
                cob.aload(rawSlot[i]);
                cob.goto_(stored);
                cob.labelBinding(boxIt);
                loadLocal(cob, t, componentSlot[i]);
                ClassDesc boxDesc = Descs.of(ClassUtil.wrapperType(t));
                cob.invokestatic(boxDesc, "valueOf", MethodTypeDesc.of(boxDesc, Descs.of(t)));
                cob.labelBinding(stored);
            } else {
                loadLocal(cob, t, componentSlot[i]);
            }
            cob.aastore();
        }
        cob.aload(pendingSlot);
        cob.invokevirtual(base, "_recordBuild", MTD_RECORD_BUILD);
        cob.astore(beanSlot);
        cob.labelBinding(finish);
        // Forward references and buffered unknown properties, in stock's
        // order (see _recordFinish); neither on the common path.
        Label finishRest = cob.newLabel();
        Label finished = cob.newLabel();
        cob.aload(referringsSlot).ifnonnull(finishRest);
        cob.aload(unknownSlot).ifnull(finished);
        cob.labelBinding(finishRest);
        cob.aload(0).aload(ctxt).aload(beanSlot).aload(referringsSlot).aload(unknownSlot);
        cob.invokevirtual(base, "_recordFinish", MTD_RECORD_FINISH);
        cob.astore(beanSlot);
        cob.labelBinding(finished);
        cob.aload(beanSlot).areturn();

        cob.labelBinding(unknown);
        cob.aload(0).aload(parser).aload(ctxt).aload(unknownSlot);
        cob.invokevirtual(base, "_recordUnknown", MTD_RECORD_UNKNOWN);
        cob.astore(unknownSlot);
        nextNameMatch(cob, parser, matcherSlot, ixSlot);
        cob.goto_(loop);

        cob.labelBinding(oddToken);
        cob.aload(0).aload(parser).aload(ctxt).aconst_null()
           .invokevirtual(base, "_unexpectedToken", MTD_UNEXPECTED)
           .areturn();

        emitPropertyHandler(cob, base, propHandler, ctxt, propSlot, excSlot, -1, null);
        emitDelegateTail(cob, base, parser, ctxt, delegate);

        Label scopeEnd = cob.newBoundLabel();
        cob.localVariable(parser, "p", CD_JSON_PARSER, scopeStart, scopeEnd);
        cob.localVariable(ctxt, "ctxt", CD_DESER_CONTEXT, scopeStart, scopeEnd);
        for (int i = 0; i < n; i++) {
            Class<?> t = props.get(i).type();
            // Record component names: Java identifiers, unlike JSON names.
            cob.localVariable(componentSlot[i], plan.localNames()[i],
                    t.isPrimitive() ? Descs.of(t) : CD_OBJECT, scopeStart, scopeEnd);
        }
        cob.localVariable(matcherSlot, "matcher", CD_NAME_MATCHER, scopeStart, scopeEnd);
        cob.localVariable(ixSlot, "ix", ConstantDescs.CD_int, scopeStart, scopeEnd);
        cob.localVariable(seenSlot, "seen", ConstantDescs.CD_long, scopeStart, scopeEnd);
        cob.localVariable(rawPrimSlot, "rawPrimitives", ConstantDescs.CD_long,
                scopeStart, scopeEnd);
        cob.localVariable(propSlot, "prop", CD_SETTABLE_PROP, scopeStart, scopeEnd);
        cob.localVariable(excSlot, "e", CD_THROWABLE, scopeStart, scopeEnd);
        cob.localVariable(unknownSlot, "unknown", CD_OBJECT, scopeStart, scopeEnd);
        cob.localVariable(beanSlot, "bean", CD_OBJECT, scopeStart, scopeEnd);
        cob.localVariable(pendingSlot, "ctorFailure", CD_THROWABLE, scopeStart, scopeEnd);
        cob.localVariable(referringsSlot, "referrings", CD_OBJECT, scopeStart, scopeEnd);
        if (views == ViewStrategy.MASK) {
            cob.localVariable(maskSlot, "viewMask", ConstantDescs.CD_long, scopeStart, scopeEnd);
        }
    }

    // A record component local: its slot and type, and (primitive components
    // only) its raw-value slot and its bit in the rawPrimitives mask.
    private record ComponentLocal(int slot, Class<?> type, int rawPrimSlot, int rawSlot,
            int index) {}

    private static void emitRecordScalar(CodeBuilder cob, int parser, int ctxt,
            ComponentLocal local, String getter, MethodTypeDesc getterType, String stockName,
            String expectedToken) {
        Label useStock = cob.newLabel();
        Label done = cob.newLabel();
        emitExpectedTokenCheck(cob, parser, expectedToken, useStock);
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, getter, getterType);
        storeLocal(cob, local.type(), local.slot());
        if (local.type().isPrimitive()) {
            emitRawPrimitive(cob, local, false);
        }
        cob.goto_(done);
        cob.labelBinding(useStock);
        emitStockValueToLocal(cob, parser, ctxt, local, stockName);
        cob.labelBinding(done);
    }

    // Reads the whole value through the stock property (parser positioned on
    // the value token). Reference components stay Object, since the erased
    // constructor handle casts on invocation. Primitive components unbox an
    // exact wrapper; anything else a custom deserializer may return (null,
    // another wrapper type) is kept raw and marks the component, so that the
    // stock creator receives it as it would without Blackbird, and widens it,
    // or fails in the creator call, as stock does.
    private static void emitStockValueToLocal(CodeBuilder cob, int parser, int ctxt,
            ComponentLocal local, String stockName) {
        ldcData(cob, stockName, CD_SETTABLE_PROP);
        cob.aload(parser).aload(ctxt);
        cob.invokevirtual(CD_SETTABLE_PROP, "deserialize", MTD_DESERIALIZE);
        Class<?> type = local.type();
        if (!type.isPrimitive()) {
            cob.astore(local.slot());
            return;
        }
        Label raw = cob.newLabel();
        Label done = cob.newLabel();
        ClassDesc boxDesc = Descs.of(ClassUtil.wrapperType(type));
        cob.dup();
        cob.instanceOf(boxDesc);
        cob.ifeq(raw);
        cob.checkcast(boxDesc);
        cob.invokevirtual(boxDesc, type.getName() + "Value", MethodTypeDesc.of(Descs.of(type)));
        storeLocal(cob, type, local.slot());
        emitRawPrimitive(cob, local, false);
        cob.goto_(done);
        cob.labelBinding(raw);
        cob.astore(local.rawSlot());
        emitRawPrimitive(cob, local, true);
        cob.labelBinding(done);
    }

    // Sets or clears the component's bit in the rawPrimitives mask: a later
    // value for a duplicate name replaces an earlier one, as in stock.
    private static void emitRawPrimitive(CodeBuilder cob, ComponentLocal local, boolean isRaw) {
        long bit = 1L << local.index();
        cob.lload(local.rawPrimSlot());
        if (isRaw) {
            cob.loadConstant(bit);
            cob.lor();
        } else {
            cob.loadConstant(~bit);
            cob.land();
        }
        cob.lstore(local.rawPrimSlot());
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

    private static void loadLocal(CodeBuilder cob, Class<?> type, int slot) {
        if (type == long.class) {
            cob.lload(slot);
        } else if (type == double.class) {
            cob.dload(slot);
        } else if (type == float.class) {
            cob.fload(slot);
        } else if (type.isPrimitive()) {
            cob.iload(slot);
        } else {
            cob.aload(slot);
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

    // The stock deserialize of the codec's own (copied) stock state.
    private static void emitDelegateTail(CodeBuilder cob, ClassDesc base, int parser, int ctxt,
            Label delegate) {
        cob.labelBinding(delegate);
        cob.aload(0).aload(parser).aload(ctxt);
        cob.invokevirtual(base, "_stockDeserialize", MTD_DESERIALIZE);
        cob.areturn();
    }

    // Branches to useStock unless the current (value) token is the one the
    // inline read expects. Null, quoted scalars, and mismatched shapes all
    // take the stock property, which owns coercion and null handling.
    // expectedToken null selects the boolean pair.
    private static void emitExpectedTokenCheck(CodeBuilder cob, int parser,
            String expectedToken, Label useStock) {
        // The fused match already advanced to the value token, so read it
        // with currentToken rather than advancing again.
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

    // The builder failure handlers' name arguments: the locals holding the
    // input name and the entry kind, or null and false where the names
    // cannot differ (NONE).
    private record Names(int inputNameSlot, int startObjectSlot) {
        static final Names NONE = new Names(-1, -1);

        void load(CodeBuilder cob) {
            if (inputNameSlot < 0) {
                cob.aconst_null().iconst_0();
            } else {
                cob.aload(inputNameSlot).iload(startObjectSlot);
            }
        }
    }

    // receiverSlot < 0 selects aconst_null (record mode: no instance yet);
    // non-null names (builder mode) passes the name arguments.
    private static void emitPropertyHandler(CodeBuilder cob, ClassDesc base, Label handler,
            int ctxt,
            int propSlot, int excSlot, int receiverSlot, Names names) {
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
        if (names != null) {
            names.load(cob);
            cob.aload(ctxt);
            cob.invokevirtual(base, "_propertyException", MTD_BUILDER_PROP_WRAP);
        } else {
            cob.aload(ctxt);
            cob.invokevirtual(base, "_propertyException", MTD_PROP_WRAP);
        }
        cob.athrow();
    }

    // The fused name match: on a non-negative result the current token is the
    // property's value token, so the arms consume it directly; negative
    // results leave the stream on the name exactly as nextNameMatch does.
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
    // arm expects. startObjectSlot >= 0 records which entry was taken.
    private static void emitFirstMatch(CodeBuilder cob, int parser, int matcherSlot, int ixSlot,
            int startObjectSlot) {
        Label nameEntry = cob.newLabel();
        Label done = cob.newLabel();
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "currentToken", MTD_NEXT_TOKEN);
        cob.getstatic(CD_JSON_TOKEN, "PROPERTY_NAME", CD_JSON_TOKEN);
        cob.if_acmpeq(nameEntry);
        if (startObjectSlot >= 0) {
            cob.iconst_1().istore(startObjectSlot);
        }
        nextNameMatch(cob, parser, matcherSlot, ixSlot);
        cob.goto_(done);
        cob.labelBinding(nameEntry);
        if (startObjectSlot >= 0) {
            cob.iconst_0().istore(startObjectSlot);
        }
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
