package tools.jackson.module.blackbird.deser;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.core.util.Named;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.CreatorProperty;
import tools.jackson.databind.deser.SettableAnyProperty;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.deser.bean.BeanPropertyMap;
import tools.jackson.databind.deser.bean.PropertyBasedCreator;
import tools.jackson.databind.deser.impl.MethodProperty;
import tools.jackson.databind.deser.jdk.NumberDeserializers;
import tools.jackson.databind.deser.jdk.StringDeserializer;
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
import tools.jackson.module.blackbird.internal.GeneratedBuilderBase;
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
    // Exact classes: a subclass may read differently
    private static final Set<Class<?>> STOCK_SCALAR_DESERS = Set.of(
            StringDeserializer.class,
            NumberDeserializers.IntegerDeserializer.class,
            NumberDeserializers.LongDeserializer.class,
            NumberDeserializers.BooleanDeserializer.class);

    private BBReaderFactory() {}

    /**
     * The resolved stock state a codec is generated from, read by the
     * placeholder (a stock deserializer subclass) from its own fields.
     * buildMethod is non-null exactly for builder-based beans. vanilla is
     * the resolved _vanillaProcessing: whether stock takes its vanilla loop.
     */
    record Source(BeanDeserializerBase deser, SettableAnyProperty anySetter,
            boolean injectables, boolean vanilla, BeanPropertyMap beanProperties, PropertyBasedCreator creator,
            boolean nonStandardCreation, boolean hasDelegateCreator, boolean hasExternalTypeId,
            boolean hasUnwrapped, AnnotatedMethod buildMethod) {}

    static ValueDeserializer<Object> tryGenerate(Source stock, DeserializationContext ctxt,
            boolean declaresViews) {
        BeanDeserializerBase delegate = stock.deser();
        try {
            ValueDeserializer<Object> codec = generate(stock, ctxt, declaresViews);
            CodegenDebug.log(() -> "reader tryGenerate " + delegate.handledType().getName()
                    + " -> " + (codec == null ? "null (gated)" : codec.getClass().getName()));
            return codec;
        } catch (StackOverflowError e) {
            // Generation ran out of stack deep in a recursive resolution: not
            // a generator bug, and the stock path, which needs no generation,
            // can still complete the call. The type keeps the stock path for
            // the life of the mapper, so it is at least logged.
            CodegenFallbacks.stackExhausted("reader", delegate.handledType());
            return null;
        } catch (VirtualMachineError e) {
            // Out of memory or a broken JVM, not a generator bug: never demote
            throw e;
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
        if (stock.buildMethod() != null) {
            return generateBuilder(delegate, ctxt, beanClass, stock, declaresViews);
        }
        if (beanClass.isRecord()) {
            return generateRecord(delegate, ctxt, beanClass, stock, declaresViews);
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
                CodegenDebug.log("reader demote: ctor access, instantiator mode");
            }
        }
        return generateWithStockMatcher(delegate, ctxt, beanClass, stock, construction,
                declaresViews, null);
    }

    // Builder-based beans: the stock ValueInstantiator creates the builder,
    // properties apply through normalized (Object, eV)Object setter handles,
    // and the build method - a constant (Object)Object handle - produces the
    // value.
    private static ValueDeserializer<Object> generateBuilder(BeanDeserializerBase delegate,
            DeserializationContext ctxt, Class<?> builderClass, Source stock,
            boolean declaresViews)
            throws ReflectiveOperationException {
        if (stock.nonStandardCreation()) {
            return gate("builder with non-standard creation");
        }
        Method build = stock.buildMethod().getAnnotated();
        if (build == null) {
            return gate("builder without build method");
        }
        // The builder class itself may be private: the codec never names it
        // (the instantiator creates it, and the handles are erased).
        if (build.getParameterCount() != 0) {
            return gate("builder shape");
        }
        MethodHandle buildMH;
        try {
            buildMH = MemberHandles.unary(build);
        } catch (IllegalAccessException e) {
            return gate("build method access");
        }
        return generateWithStockMatcher(delegate, ctxt, builderClass, stock,
                Construction.builder(delegate.getValueInstantiator(), buildMH), declaresViews,
                builderClass);
    }

    // POJO and builder codecs reuse the stock name matcher: the bean
    // property map's matcher, with aliases after the primary names and the
    // stock case-insensitivity. Initialized here, during resolve; the
    // placeholders then skip stock createContextual's rebuild of it (see
    // BBReaderPlaceholder.initNameMatcher).
    private static ValueDeserializer<Object> generateWithStockMatcher(
            BeanDeserializerBase delegate, DeserializationContext ctxt, Class<?> beanClass,
            Source stock, Construction construction,
            boolean declaresViews, Class<?> builderClass)
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
            props.add(builderClass == null ? classify(prop)
                    : classifyBuilder(prop, builderClass));
        }
        int[] matchArms = matchArms(byIndex, primaries);
        if (matchArms == null) {
            return gate("matcher layout");
        }
        return ReaderCodegen.generate(beanClass, props, beanProps.getNameMatcher(), matchArms,
                delegate, beanProps, construction, stock.injectables(),
                (builderClass == null) && stock.vanilla(),
                viewStrategy(ctxt, delegate, props, declaresViews), null);
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

    // With DEFAULT_VIEW_INCLUSION on, unannotated properties have no views,
    // so the resolved hasViews() (which also sees views assigned in code, by
    // updateBuilder or a property definition) tells exactly whether views
    // can affect the bean; without any, no per-arm view code is emitted
    // (NONE). With inclusion off, disabled inclusion forces the empty set
    // onto every unannotated property, so hasViews() cannot tell a declared
    // view apart; beans that declare no @JsonView (read from the property
    // definitions at modify time) have every property hidden under an active
    // view, so the degenerate view-active call DELEGATEs to stock and the
    // no-view hot path stays free of mask tests. Otherwise MASK (the fast
    // path under views) up to 64 properties, DELEGATE beyond. Mask
    // visibility is read per property from SettableBeanProperty.visibleInView,
    // so semantics stay exactly stock.
    private static ViewStrategy viewStrategy(DeserializationContext ctxt,
            BeanDeserializerBase delegate, List<GenProp> props, boolean declaresViews) {
        if (ctxt.isEnabled(MapperFeature.DEFAULT_VIEW_INCLUSION)) {
            if (!delegate.hasViews()) {
                return ViewStrategy.NONE;
            }
        } else if (!declaresViews) {
            return ViewStrategy.DELEGATE;
        }
        return (props.size() > 64) ? ViewStrategy.DELEGATE : ViewStrategy.MASK;
    }

    // Records collect components into typed locals and construct through the
    // canonical constructor; every property must be a plain CreatorProperty
    // whose creator index and type line up with the record components.
    private static ValueDeserializer<Object> generateRecord(BeanDeserializerBase delegate,
            DeserializationContext ctxt, Class<?> beanClass, Source stock,
            boolean declaresViews)
            throws ReflectiveOperationException {
        PropertyBasedCreator creator = stock.creator();
        // Stock prefers a delegating creator to the property-based one, and
        // external type ids, unwrapped properties, any-setters (also as a
        // creator parameter) and injectables take paths the typed-locals loop
        // does not model.
        if (creator == null || stock.hasDelegateCreator() || stock.hasExternalTypeId()
                || stock.hasUnwrapped() || stock.anySetter() != null
                || stock.injectables()) {
            return gate("record creation is not the plain canonical constructor");
        }
        // The generated code constructs through the canonical constructor and
        // mirrors the failure handling of the stock instantiator. A
        // @JsonCreator factory or a custom instantiator must construct
        // through the instantiator, so such records stay on the stock path.
        // An AnnotatedConstructor that also passes the per-component index
        // and type match below is the canonical constructor: a second
        // constructor with the same signature cannot exist.
        if (delegate.getValueInstantiator().getClass() != StdValueInstantiator.class
                || !(delegate.getValueInstantiator().getWithArgsCreator()
                        instanceof AnnotatedConstructor ctor)) {
            return gate("record creator is not the constructor");
        }
        RecordComponent[] comps = beanClass.getRecordComponents();
        // The seen-component mask in the generated code is one long.
        if (comps.length > 64 || comps.length == 0) {
            return gate("record size");
        }
        SettableBeanProperty[] creatorProps = creator.allPropertiesInOrder();
        if (creatorProps.length != comps.length) {
            return gate("record creator arity");
        }
        // Stock reads names through the creator, then through the bean
        // properties for non-creator ones; records whose bean properties are
        // not all creator properties keep the stock path.
        for (SettableBeanProperty prop : stock.beanProperties()) {
            if (!(prop instanceof CreatorProperty)) {
                return gate("record with non-creator property " + prop.getName());
            }
        }
        for (int i = 0; i < comps.length; i++) {
            SettableBeanProperty prop = creatorProps[i];
            // Wrapper classes (managed references among them) do work at
            // construction that the typed-locals loop does not.
            if (prop == null || prop.getClass() != CreatorProperty.class
                    || prop.getCreatorIndex() != i
                    || prop.getMetadata().getMergeInfo() != null
                    || prop.getInjectionDefinition() != null
                    || prop.getType().getRawClass() != comps[i].getType()) {
                return gate("record component " + i);
            }
        }
        MethodHandle recordCtor;
        try {
            recordCtor = MemberHandles.constructor(ctor.getAnnotated());
        } catch (IllegalAccessException e) {
            // The lookup cannot reach the canonical constructor, so databind
            // could not open it either: demote, stock fails the same way at
            // first use.
            return gate("record ctor access");
        }
        List<GenProp> props = new ArrayList<>(comps.length);
        String[] localNames = new String[comps.length];
        for (int i = 0; i < comps.length; i++) {
            Class<?> compType = comps[i].getType();
            SettableBeanProperty prop = creatorProps[i];
            localNames[i] = comps[i].getName();
            ValueDeserializer<?> valueDeser = prop.getValueDeserializer();
            Kind kind;
            ValueDeserializer<?> child = null;
            if (prop.getValueTypeDeserializer() != null) {
                kind = Kind.STOCK;
            } else if (isCodec(valueDeser)) {
                kind = Kind.CHILD;
                child = valueDeser;
            } else {
                kind = scalarKind(compType);
                if (kind == null || valueDeser == null
                        || !STOCK_SCALAR_DESERS.contains(valueDeser.getClass())) {
                    kind = Kind.STOCK;
                }
            }
            props.add(new GenProp(prop.getName(), kind, prop, compType, child, null));
        }
        // The stock fallback paths of the codec read non-creator names
        // through the bean property matcher, which createContextual would
        // initialize next.
        BeanPropertyMap beanProps = stock.beanProperties();
        beanProps.initMatcher(ctxt.tokenStreamFactory());
        RecordNames names = recordNames(ctxt, creator, beanProps, props);
        return ReaderCodegen.generate(beanClass, props, names.matcher(), names.matchArms(),
                delegate, beanProps, Construction.record(recordCtor), false, false,
                viewStrategy(ctxt, delegate, props, declaresViews), localNames);
    }

    private record RecordNames(PropertyNameMatcher matcher, int[] matchArms) {}

    // Stock reads a record's names through PropertyBasedCreator
    // .findCreatorProperty: a map of primary names and aliases (lower-cased
    // with Locale.ROOT when case-insensitive) without the names to ignore.
    // The matcher here holds exactly the names that map finds for each
    // component. A component whose name it does not find (an ignored one)
    // gets no name: the document's value takes the unknown path, as in
    // stock, and the component keeps the stock absent value.
    private static RecordNames recordNames(DeserializationContext ctxt,
            PropertyBasedCreator creator, BeanPropertyMap beanProps, List<GenProp> props) {
        List<Named> names = new ArrayList<>();
        List<Integer> arms = new ArrayList<>();
        for (int i = 0; i < props.size(); i++) {
            SettableBeanProperty prop = props.get(i).stock();
            if (creator.findCreatorProperty(prop.getName()) == prop) {
                names.add(Named.fromString(prop.getName()));
                arms.add(i);
            }
        }
        for (int i = 0; i < props.size(); i++) {
            SettableBeanProperty prop = props.get(i).stock();
            for (PropertyName alias : prop.findAliases(ctxt.getConfig())) {
                String name = alias.getSimpleName();
                if (creator.findCreatorProperty(name) == prop) {
                    names.add(Named.fromString(name));
                    arms.add(i);
                }
            }
        }
        // false: the matcher interns the names itself.
        PropertyNameMatcher matcher = beanProps.isCaseInsensitive()
                ? ctxt.tokenStreamFactory().constructCINameMatcher(names, false, Locale.ROOT)
                : ctxt.tokenStreamFactory().constructNameMatcher(names, false);
        int[] matchArms = new int[arms.size()];
        for (int k = 0; k < matchArms.length; k++) {
            matchArms[k] = arms.get(k);
        }
        return new RecordNames(matcher, matchArms);
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

    // Builder setters must return void or the builder class exactly (the
    // normalized handle keeps the fluent return as the new builder, which
    // is what stock deserializeSetAndReturn does with a non-null result).
    private static GenProp classifyBuilder(SettableBeanProperty prop, Class<?> builderClass) {
        if (prop.getClass() != MethodProperty.class || prop.getValueTypeDeserializer() != null) {
            return stock(prop);
        }
        Class<?> raw = prop.getType().getRawClass();
        Method setter = setterOf(prop, raw);
        if (setter == null
                || (setter.getReturnType() != void.class
                        && setter.getReturnType() != builderClass)) {
            return stock(prop);
        }
        MethodHandle handle;
        try {
            handle = MemberHandles.builderSetter(setter, raw);
        } catch (IllegalAccessException | RuntimeException e) {
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
                || !STOCK_SCALAR_DESERS.contains(valueDeser.getClass())) {
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
        return (deser instanceof GeneratedReaderBase) || (deser instanceof GeneratedBuilderBase);
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
