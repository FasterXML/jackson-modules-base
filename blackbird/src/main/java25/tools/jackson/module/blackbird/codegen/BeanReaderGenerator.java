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
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueInstantiator;
import tools.jackson.databind.deser.bean.BeanDeserializer;
import tools.jackson.databind.deser.bean.BeanPropertyMap;
import tools.jackson.module.blackbird.codegen.ReaderCodegen.GenProp;
import tools.jackson.module.blackbird.codegen.ReaderCodegen.Mode;
import tools.jackson.module.blackbird.codegen.ReaderCodegen.ViewStrategy;
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
    private static final ClassDesc CD_NAME_MATCHER = Descs.of(PropertyNameMatcher.class);
    private static final ClassDesc CD_SETTABLE_PROP = Descs.of(SettableBeanProperty.class);
    private static final ClassDesc CD_READER_BASE = Descs.of(GeneratedReaderBase.class);
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
    private static final MethodTypeDesc MTD_CREATE_DEFAULT =
            MethodTypeDesc.of(CD_OBJECT, CD_DESER_CONTEXT);
    private static final MethodTypeDesc MTD_NEW_INSTANCE = MethodTypeDesc.of(CD_OBJECT);
    private static final MethodTypeDesc MTD_INJECT = MethodTypeDesc.of(
            ConstantDescs.CD_void, CD_DESER_CONTEXT, CD_OBJECT);
    private static final MethodTypeDesc MTD_HANDLE_UNKNOWN = MethodTypeDesc.of(
            ConstantDescs.CD_void, CD_JSON_PARSER, CD_DESER_CONTEXT, CD_OBJECT);
    private static final MethodTypeDesc MTD_CREATION_FAILURE =
            MethodTypeDesc.of(CD_OBJECT, CD_DESER_CONTEXT, CD_THROWABLE);
    private static final MethodTypeDesc MTD_PROP_WRAP = MethodTypeDesc.of(
            CD_RTE, CD_THROWABLE, CD_OBJECT, CD_SETTABLE_PROP, CD_DESER_CONTEXT);
    private static final MethodTypeDesc MTD_STORE_FAILURE = MethodTypeDesc.of(CD_RTE,
            CD_THROWABLE, CD_OBJECT, CD_OBJECT, CD_SETTABLE_PROP, CD_JSON_PARSER,
            CD_DESER_CONTEXT);
    private static final MethodTypeDesc MTD_GET_ACTIVE_VIEW =
            MethodTypeDesc.of(ConstantDescs.CD_Class);
    private static final MethodTypeDesc MTD_VIEW_MASK =
            MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_Class);
    private static final MethodTypeDesc MTD_HIDDEN_VIEW = MethodTypeDesc.of(
            ConstantDescs.CD_void, CD_JSON_PARSER, CD_DESER_CONTEXT, CD_SETTABLE_PROP);
    private static final MethodTypeDesc MTD_VISIBLE_IN_VIEW =
            MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_Class);

    // Generated code resolves class-data entries by NAME through the
    // classDataEntry bootstrap on the base class (the JDK classDataAt
    // bootstrap rejects any condy name but "_"); see ReaderCodegen.dataName.
    private static final java.lang.constant.DirectMethodHandleDesc BSM_DATA_ENTRY =
            ConstantDescs.ofConstantBootstrap(CD_READER_BASE, "classDataEntry", CD_OBJECT);

    // Stack map frames can need the supertypes of the types the code uses.
    // The default resolver reads class files through the system class
    // loader, which does not see Jackson in an application server, an OSGi
    // container or a fat jar. The lookup of this module sees it.
    static final ClassFile CLASS_FILE = ClassFile.of(
            ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.defaultResolver()
                    .orElse(ClassHierarchyResolver.ofClassLoading(MethodHandles.lookup()))));

    private BeanReaderGenerator() {}

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
        ClassDesc base = CD_READER_BASE;
        MethodTypeDesc ctor = MTD_READER_CTOR;
        return CLASS_FILE.build(thisClass, clb -> {
            clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            clb.withSuperclass(base);
            clb.withMethod(ConstantDescs.INIT_NAME, ctor, ClassFile.ACC_PUBLIC,
                    mb -> mb.with(params("src", "beanProperties", "layout"))
                            .withCode(cob -> cob.aload(0).aload(1).aload(2).aload(3)
                                    .invokespecial(base, ConstantDescs.INIT_NAME, ctor)
                                    .return_()));
            clb.withMethod("deserialize", MTD_DESERIALIZE, ClassFile.ACC_PUBLIC,
                    mb -> mb.with(params("p", "ctxt"))
                            .withCode(cob -> buildDeserialize(cob, plan)));
            if (plan.views() == ViewStrategy.MASK) {
                emitComputeViewMask(clb, plan.props(), plan.stockName());
            }
        });
    }

    static void ldcData(CodeBuilder cob, String name, ClassDesc type) {
        cob.ldc(DynamicConstantDesc.ofNamed(BSM_DATA_ENTRY, name, type));
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

    // The loop head: dispatch on the match index (arms,
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

    private static void buildDeserialize(CodeBuilder cob, ReaderCodegen.Plan plan) {
        final ClassDesc base = CD_READER_BASE;
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
        cob.aload(parser).aload(beanSlot)
           .invokevirtual(CD_JSON_PARSER, "assignCurrentValue", MTD_ASSIGN_CURRENT);
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

        emitFirstMatch(cob, parser, matcherSlot, ixSlot);
        Label[] caseLabels = emitLoopHead(cob, plan, ixSlot, loop, endObject, unknown,
                oddToken, defaultCase);

        for (int i = 0; i < props.size(); i++) {
            cob.labelBinding(caseLabels[i]);
            if (views == ViewStrategy.MASK) {
                emitArmViewCheck(cob, base, parser, ctxt, maskSlot, matcherSlot, ixSlot, i,
                        plan.stockName()[i], loop);
            }
            Label armEnd = beginArm(cob, plan.stockName()[i], propSlot, propHandler);
            GenProp prop = props.get(i);
            Store store = (prop.handle() == null) ? null
                    : new Store(plan.handleName()[i], storeStart[i], storeEnd[i]);
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
                    emitStockSet(cob, parser, ctxt, beanSlot, stockName);
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
        cob.aload(0).aload(parser).aload(ctxt).aload(beanSlot);
        cob.invokevirtual(base, "_handleUnknown", MTD_HANDLE_UNKNOWN);
        nextNameMatch(cob, parser, matcherSlot, ixSlot);
        cob.goto_(loop);

        cob.labelBinding(oddToken);
        cob.aload(0).aload(parser).aload(ctxt).aload(beanSlot)
           .invokevirtual(base, "_unexpectedToken", MTD_UNEXPECTED)
           .areturn();

        emitPropertyHandler(cob, base, propHandler, ctxt, propSlot, excSlot, beanSlot);
        if (anyStore) {
            cob.labelBinding(storeHandler);
            cob.astore(excSlot);
            cob.aload(0).aload(excSlot).aload(beanSlot).aload(valueSlot).aload(propSlot)
               .aload(parser).aload(ctxt);
            cob.invokevirtual(base, "_storeFailure", MTD_STORE_FAILURE);
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
        cob.localVariable(beanSlot, "bean", CD_OBJECT, scopeStart, scopeEnd);
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
    }

    // A generated store: the class-data name of its (Object, eV)void handle
    // and the labels of its exception region.
    private record Store(String handleName, Label start, Label end) {}

    private static void emitStore(CodeBuilder cob, Store store, ClassDesc erasedDesc) {
        cob.labelBinding(store.start());
        cob.invokevirtual(ConstantDescs.CD_MethodHandle, "invokeExact", MethodTypeDesc.of(
                ConstantDescs.CD_void, CD_OBJECT, erasedDesc));
        cob.labelBinding(store.end());
    }

    // The null branch (and any other unexpected token) enters the stock
    // property with the parser on that token, which is the position its
    // deserializeAndSet expects. Without a generated store (the property
    // keeps its stock arm whatever the token), only the stock call remains.
    private static void emitScalar(CodeBuilder cob, int receiverSlot, int parser, int ctxt,
            String getter, MethodTypeDesc getterType, ClassDesc erasedDesc,
            String stockName, Store store, String expectedToken) {
        Label useStock = cob.newLabel();
        Label done = cob.newLabel();
        emitExpectedTokenCheck(cob, parser, expectedToken, useStock);
        ldcData(cob, store.handleName(), ConstantDescs.CD_MethodHandle);
        cob.aload(receiverSlot);
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, getter, getterType);
        emitStore(cob, store, erasedDesc);
        cob.goto_(done);
        cob.labelBinding(useStock);
        emitStockSet(cob, parser, ctxt, receiverSlot, stockName);
        cob.labelBinding(done);
    }

    // A child bean on START_OBJECT goes to the linked child codec, anything
    // else to the stock property. The child value passes through valueSlot so
    // that a failing store can name its class, as stock does.
    private static void emitChildArm(CodeBuilder cob, int receiverSlot, int parser, int ctxt,
            int valueSlot, String childName, String stockName, Store store) {
        Label childStock = cob.newLabel();
        Label childDone = cob.newLabel();
        // The fused match left the value token current.
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "currentToken", MTD_NEXT_TOKEN);
        cob.getstatic(CD_JSON_TOKEN, "START_OBJECT", CD_JSON_TOKEN);
        cob.if_acmpne(childStock);
        emitChildCall(cob, parser, ctxt, childName);
        cob.astore(valueSlot);
        ldcData(cob, store.handleName(), ConstantDescs.CD_MethodHandle);
        cob.aload(receiverSlot);
        cob.aload(valueSlot);
        emitStore(cob, store, CD_OBJECT);
        cob.goto_(childDone);
        cob.labelBinding(childStock);
        emitStockSet(cob, parser, ctxt, receiverSlot, stockName);
        cob.labelBinding(childDone);
    }

    // The constant receiver makes the call monomorphic.
    private static void emitChildCall(CodeBuilder cob, int parser, int ctxt, String childName) {
        ldcData(cob, childName, CD_VALUE_DESERIALIZER);
        cob.aload(parser).aload(ctxt);
        cob.invokevirtual(CD_VALUE_DESERIALIZER, "deserialize", MTD_DESERIALIZE);
    }

    // The stock property reads and stores the value (deserializeAndSet).
    private static void emitStockSet(CodeBuilder cob, int parser, int ctxt, int receiverSlot,
            String stockName) {
        ldcData(cob, stockName, CD_SETTABLE_PROP);
        cob.aload(parser).aload(ctxt).aload(receiverSlot);
        cob.invokevirtual(CD_SETTABLE_PROP, "deserializeAndSet", MTD_DESERIALIZE_AND_SET);
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

    private static void emitPropertyHandler(CodeBuilder cob, ClassDesc base, Label handler,
            int ctxt,
            int propSlot, int excSlot, int receiverSlot) {
        cob.labelBinding(handler);
        cob.astore(excSlot);
        cob.aload(0);
        cob.aload(excSlot);
        cob.aload(receiverSlot);
        cob.aload(propSlot);
        cob.aload(ctxt);
        cob.invokevirtual(base, "_propertyException", MTD_PROP_WRAP);
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
