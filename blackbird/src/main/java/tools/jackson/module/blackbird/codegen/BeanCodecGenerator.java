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
    public enum Kind { STRING, INT, LONG, BOOLEAN, STOCK }

    public record GenProp(String name, Kind kind, Method setter, SettableBeanProperty stock) {}

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

    @SuppressWarnings("unchecked")
    public static ValueDeserializer<Object> generate(Class<?> beanClass, List<GenProp> props,
            PropertyNameMatcher matcher, BeanDeserializerBase fallback)
            throws ReflectiveOperationException {
        List<Object> classData = new ArrayList<>();
        classData.add(matcher);
        // Tier-A properties also carry their SettableBeanProperty: their
        // VALUE_NULL branch runs through it, since null handling is
        // configuration-dependent per property.
        int[] stockIndex = new int[props.size()];
        for (int i = 0; i < props.size(); i++) {
            stockIndex[i] = classData.size();
            classData.add(props.get(i).stock());
        }

        byte[] bytes = buildClass(beanClass, props, stockIndex);
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

    private static byte[] buildClass(Class<?> beanClass, List<GenProp> props, int[] stockIndex) {
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
                    cob -> buildDeserialize(cob, beanClass, props, stockIndex));
        });
    }

    private static void buildDeserialize(CodeBuilder cob, Class<?> beanClass,
            List<GenProp> props, int[] stockIndex) {
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
                        "getString", MTD_GET_STRING, ConstantDescs.CD_String, stockIndex[i]);
                case INT -> emitScalar(cob, beanSlot, parser, ctxt, beanDesc, prop,
                        "getIntValue", MTD_GET_INT, ConstantDescs.CD_int, stockIndex[i]);
                case LONG -> emitScalar(cob, beanSlot, parser, ctxt, beanDesc, prop,
                        "getLongValue", MTD_GET_LONG, ConstantDescs.CD_long, stockIndex[i]);
                case BOOLEAN -> emitScalar(cob, beanSlot, parser, ctxt, beanDesc, prop,
                        "getBooleanValue", MTD_GET_BOOLEAN, ConstantDescs.CD_boolean, stockIndex[i]);
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
        throwIse(cob, "unexpected token while matching a property name");
    }

    // The null branch enters deserializeAndSet with the parser already on the
    // VALUE_NULL token, which is the position that method expects.
    private static void emitScalar(CodeBuilder cob, int beanSlot, int parser, int ctxt,
            ClassDesc beanDesc, GenProp prop, String getter, MethodTypeDesc getterType,
            ClassDesc valueDesc, int stockIdx) {
        Label isNull = cob.newLabel();
        Label done = cob.newLabel();
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, "nextToken", MTD_NEXT_TOKEN);
        cob.getstatic(CD_JSON_TOKEN, "VALUE_NULL", CD_JSON_TOKEN);
        cob.if_acmpeq(isNull);
        cob.aload(beanSlot);
        cob.aload(parser).invokevirtual(CD_JSON_PARSER, getter, getterType);
        invokeSetter(cob, beanDesc, prop, valueDesc);
        cob.goto_(done);
        cob.labelBinding(isNull);
        emitStockSet(cob, parser, ctxt, beanSlot, stockIdx);
        cob.labelBinding(done);
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
