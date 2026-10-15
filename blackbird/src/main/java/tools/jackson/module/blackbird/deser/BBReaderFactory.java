package tools.jackson.module.blackbird.deser;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.deser.bean.BeanPropertyMap;
import tools.jackson.databind.deser.impl.MethodProperty;
import tools.jackson.databind.deser.std.StdValueInstantiator;
import tools.jackson.databind.introspect.AnnotatedConstructor;
import tools.jackson.databind.introspect.AnnotatedField;
import tools.jackson.databind.introspect.AnnotatedMethod;
import tools.jackson.module.blackbird.codegen.CodegenDebug;
import tools.jackson.module.blackbird.codegen.CodegenFallbacks;
import tools.jackson.module.blackbird.codegen.MemberHandles;
import tools.jackson.module.blackbird.codegen.ReaderCodegen;
import tools.jackson.module.blackbird.codegen.ReaderCodegen.Construction;
import tools.jackson.module.blackbird.codegen.ReaderCodegen.GenProp;
import tools.jackson.module.blackbird.codegen.ReaderCodegen.Kind;
import tools.jackson.module.blackbird.codegen.ReaderCodegen.ViewStrategy;
import tools.jackson.module.blackbird.internal.GeneratedReaderBase;

/**
 * Builds a generated codec from a resolved stock bean deserializer, or returns
 * null when the bean does not qualify. The codec handles a bean only when
 * every feature of the bean has a generated path that matches stock; anything
 * else keeps the stock deserializer (a gate) or the stock property inside the
 * codec (a STOCK arm). Every decision stock databind has already made - which
 * names are ignored or included, how names match (aliases, case
 * insensitivity), which creator builds the bean - is read from the resolved
 * stock deserializer, not computed again. Member access goes through
 * {@link MemberHandles}, which reaches exactly what stock databind can
 * invoke, so accessibility never gates a bean the stock path handles - an
 * access failure demotes to stock, which fails the same way.
 */
final class BBReaderFactory
{
    private static final Set<String> STOCK_SCALAR_DESERS = Set.of(
            "tools.jackson.databind.deser.jdk.StringDeserializer",
            "tools.jackson.databind.deser.jdk.NumberDeserializers$IntegerDeserializer",
            "tools.jackson.databind.deser.jdk.NumberDeserializers$LongDeserializer",
            "tools.jackson.databind.deser.jdk.NumberDeserializers$BooleanDeserializer");

    private BBReaderFactory() {}

    /**
     * The resolved stock state a codec is generated from, read by the
     * placeholder (a stock deserializer subclass) from its own fields.
     */
    record Source(BeanDeserializerBase deser, boolean injectables,
            BeanPropertyMap beanProperties, boolean nonStandardCreation) {}

    static ValueDeserializer<Object> tryGenerate(Source stock, DeserializationContext ctxt,
            boolean declaresViews) {
        BeanDeserializerBase delegate = stock.deser();
        try {
            ValueDeserializer<Object> codec = generate(stock, ctxt, declaresViews);
            CodegenDebug.log(() -> "reader tryGenerate " + delegate.handledType().getName()
                    + " -> " + (codec == null ? "null (gated)" : codec.getClass().getName()));
            return codec;
        } catch (Throwable t) {
            if (CodegenDebug.ENABLED) {
                CodegenDebug.log(() -> "reader tryGenerate " + delegate.handledType().getName() + " threw:");
                t.printStackTrace();
            }
            CodegenFallbacks.generationFailure(delegate.handledType(), t);
            return null;
        }
    }

    private static ValueDeserializer<Object> gate(String reason) {
        CodegenDebug.log(() -> "reader gate: " + reason);
        return null;
    }

    private static ValueDeserializer<Object> generate(Source stock, DeserializationContext ctxt,
            boolean declaresViews)
            throws ReflectiveOperationException {
        BeanDeserializerBase delegate = stock.deser();
        // Deliberately no delegate.hasViews() gate: 3.x disables
        // DEFAULT_VIEW_INCLUSION by default, which marks every bean as needing
        // view processing; the generated codec instead checks
        // ctxt.getActiveView() per call and delegates when a view is active.
        if (delegate.getObjectIdReader(ctxt) != null) {
            return gate("object id");
        }
        Class<?> beanClass = delegate.handledType();
        if (Modifier.isAbstract(beanClass.getModifiers())) {
            return gate("abstract");
        }
        // Stock builds the bean with the default creator and runs the
        // property loop only without "non-standard creation": a delegating
        // or property-based creator (also beside a default constructor), an
        // external type id, or unwrapped properties all take other stock
        // paths, which the generated loop does not have.
        if (stock.nonStandardCreation()) {
            return gate("non-standard creation (creator, external type id or unwrapped)");
        }
        // A constant constructor handle is only equivalent when the
        // instantiator is the stock one and its default creator IS the
        // no-arg constructor (its failure handling is mirrored for exactly
        // that instantiator). Everything else that still reports
        // canCreateUsingDefault - a no-arg @JsonCreator factory, a custom
        // instantiator, a constructor the lookup cannot unreflect - constructs
        // through the instantiator held in class data instead.
        Construction construction = Construction.instantiator(delegate.getValueInstantiator());
        if (delegate.getValueInstantiator().getClass() == StdValueInstantiator.class
                && delegate.getValueInstantiator().getDefaultCreator()
                        instanceof AnnotatedConstructor ac) {
            try {
                construction = Construction.constructor(
                        MemberHandles.defaultConstructor(ac.getAnnotated()));
            } catch (IllegalAccessException e) {
                CodegenDebug.log(() -> "reader demote: ctor access, instantiator mode");
            }
        }
        return generateWithStockMatcher(delegate, ctxt, beanClass, stock, construction,
                declaresViews);
    }

    // Codecs reuse the stock name matcher: the bean property map's matcher, with aliases after the primary names and the
    // stock case-insensitivity. Stock createContextual initializes it right
    // after resolve; initializing it here builds the same matcher.
    private static ValueDeserializer<Object> generateWithStockMatcher(
            BeanDeserializerBase delegate, DeserializationContext ctxt, Class<?> beanClass,
            Source stock, Construction construction, boolean declaresViews)
            throws ReflectiveOperationException {
        BeanPropertyMap beanProps = stock.beanProperties();
        beanProps.initMatcher(ctxt.tokenStreamFactory());
        SettableBeanProperty[] byIndex = beanProps.getNameMatcherProperties();
        int primaries = beanProps.size();
        if (primaries == 0) {
            return gate("no props");
        }
        List<GenProp> props = new ArrayList<>(primaries);
        for (int i = 0; i < primaries; i++) {
            SettableBeanProperty prop = byIndex[i];
            if (prop.getMetadata().getMergeInfo() != null) {
                return gate("merge prop " + prop.getName());
            }
            props.add(classify(prop));
        }
        int[] matchArms = matchArms(byIndex, primaries);
        if (matchArms == null) {
            return gate("matcher layout");
        }
        return ReaderCodegen.generate(beanClass, props, beanProps.getNameMatcher(), matchArms,
                delegate, beanProps, construction, stock.injectables(),
                viewStrategy(ctxt, props, declaresViews));
    }

    // The stock matcher has one index per primary name (the arms, in order),
    // then alias entries that each hold their primary property; returns the
    // arm per matcher index.
    private static int[] matchArms(SettableBeanProperty[] byIndex, int primaries) {
        Map<SettableBeanProperty, Integer> arm = new IdentityHashMap<>();
        int[] out = new int[byIndex.length];
        for (int i = 0; i < byIndex.length; i++) {
            Integer a = (i < primaries) ? Integer.valueOf(i) : arm.get(byIndex[i]);
            if (a == null) {
                return null;
            }
            arm.putIfAbsent(byIndex[i], a);
            out[i] = a;
        }
        return out;
    }

    // Beans that declare no @JsonView anywhere (read from the property
    // definitions at modify time; the resolved matchers cannot distinguish a
    // declared view from the empty set that disabled DEFAULT_VIEW_INCLUSION
    // forces onto every unannotated property) emit no per-arm view code:
    // with inclusion on, views cannot affect them at all (NONE); with it off,
    // an active view hides every property, so the degenerate view-active call
    // DELEGATEs to stock and the no-view hot path stays free of mask tests.
    // Declared views take MASK (the fast path under views) up to 64
    // properties, DELEGATE beyond. Mask visibility is read per property from
    // SettableBeanProperty.visibleInView, so semantics stay exactly stock.
    private static ViewStrategy viewStrategy(DeserializationContext ctxt,
            List<GenProp> props, boolean declaresViews) {
        if (!declaresViews) {
            return ctxt.isEnabled(MapperFeature.DEFAULT_VIEW_INCLUSION)
                    ? ViewStrategy.NONE : ViewStrategy.DELEGATE;
        }
        return (props.size() > 64) ? ViewStrategy.DELEGATE : ViewStrategy.MASK;
    }

    // Generated stores are only equivalent for the plain stock property
    // class (MethodProperty, which backs setters and fields alike):
    // subclasses and wrappers (managed and back references, object id
    // references, setterless and merging properties, user wrappers from
    // updateBuilder) do their work in deserializeAndSet, so they keep it.
    // A value type deserializer (polymorphic values, default typing) is
    // applied only by the stock property.
    private static GenProp classify(SettableBeanProperty prop) {
        if (prop.getClass() != MethodProperty.class || prop.getValueTypeDeserializer() != null) {
            return stock(prop);
        }
        Class<?> raw = prop.getType().getRawClass();
        MethodHandle handle = storeHandle(prop, raw);
        if (handle == null) {
            return stock(prop);
        }
        return withValueKind(prop, raw, handle);
    }

    private static GenProp withValueKind(SettableBeanProperty prop, Class<?> raw,
            MethodHandle handle) {
        ValueDeserializer<?> valueDeser = prop.getValueDeserializer();
        if (isCodec(valueDeser)) {
            return new GenProp(prop.getName(), Kind.CHILD, prop, raw, valueDeser, handle);
        }
        Kind kind = scalarKind(raw);
        if (kind == null || valueDeser == null
                || !STOCK_SCALAR_DESERS.contains(valueDeser.getClass().getName())) {
            return stock(prop);
        }
        return new GenProp(prop.getName(), kind, prop, raw, null, handle);
    }

    // The property's store as an erased (Object, eV)void handle: a setter
    // call or a field put, whichever backs the property. Final fields stay on
    // the stock path so their (stock-defined) behavior is preserved exactly;
    // an access failure demotes the property to the stock path.
    private static MethodHandle storeHandle(SettableBeanProperty prop, Class<?> raw) {
        try {
            Method setter = setterOf(prop, raw);
            if (setter != null) {
                return MemberHandles.setter(setter, raw);
            }
            Field field = fieldOf(prop, raw);
            if (field != null && !Modifier.isFinal(field.getModifiers())) {
                return MemberHandles.fieldSetter(field);
            }
            return null;
        } catch (IllegalAccessException | RuntimeException e) {
            return null;
        }
    }

    private static Field fieldOf(SettableBeanProperty prop, Class<?> raw) {
        if (!(prop.getMember() instanceof AnnotatedField af)) {
            return null;
        }
        Field field = af.getAnnotated();
        if (field == null || Modifier.isStatic(field.getModifiers())
                || field.getType() != raw) {
            return null;
        }
        return field;
    }

    private static Method setterOf(SettableBeanProperty prop, Class<?> raw) {
        if (!(prop.getMember() instanceof AnnotatedMethod am)) {
            return null;
        }
        Method setter = am.getAnnotated();
        if (setter == null || setter.getParameterCount() != 1
                || setter.getParameterTypes()[0] != raw) {
            return null;
        }
        return setter;
    }

    private static GenProp stock(SettableBeanProperty prop) {
        CodegenDebug.log(() -> "reader prop stock: " + prop.getName());
        return new GenProp(prop.getName(), Kind.STOCK, prop);
    }

    private static boolean isCodec(ValueDeserializer<?> deser) {
        return deser instanceof GeneratedReaderBase;
    }

    private static Kind scalarKind(Class<?> raw) {
        if (raw == String.class) {
            return Kind.STRING;
        }
        if (raw == int.class) {
            return Kind.INT;
        }
        if (raw == long.class) {
            return Kind.LONG;
        }
        if (raw == boolean.class) {
            return Kind.BOOLEAN;
        }
        return null;
    }
}
