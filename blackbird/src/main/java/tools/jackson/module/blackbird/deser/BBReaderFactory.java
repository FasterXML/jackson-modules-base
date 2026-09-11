package tools.jackson.module.blackbird.deser;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.core.util.Named;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.CreatorProperty;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.introspect.AnnotatedConstructor;
import tools.jackson.databind.introspect.AnnotatedField;
import tools.jackson.databind.introspect.AnnotatedMethod;
import tools.jackson.module.blackbird.codegen.BeanReaderGenerator.GenProp;
import tools.jackson.module.blackbird.internal.GeneratedReaderBase;
import tools.jackson.module.blackbird.codegen.BeanReaderGenerator.Kind;
import tools.jackson.module.blackbird.codegen.BeanReaderGenerator.ViewStrategy;
import tools.jackson.module.blackbird.codegen.BeanReaderGenerator;
import tools.jackson.module.blackbird.codegen.CodegenFallbacks;
import tools.jackson.module.blackbird.codegen.MemberHandles;

/**
 * Builds a generated codec from a resolved stock bean deserializer, or returns
 * null when the bean does not qualify. Qualification is conservative: every
 * gate that cannot be verified cheaply demotes a property to the stock path or
 * rejects the bean entirely, so semantics never drift from databind. Member
 * access goes through {@link MemberHandles}, which reaches exactly what stock
 * databind can invoke, so accessibility never gates a bean the stock path
 * handles - an access failure demotes to stock, which fails the same way.
 */
final class BBReaderFactory
{
    private static final Set<String> STOCK_SCALAR_DESERS = Set.of(
            "tools.jackson.databind.deser.jdk.StringDeserializer",
            "tools.jackson.databind.deser.jdk.NumberDeserializers$IntegerDeserializer",
            "tools.jackson.databind.deser.jdk.NumberDeserializers$LongDeserializer",
            "tools.jackson.databind.deser.jdk.NumberDeserializers$BooleanDeserializer");

    private BBReaderFactory() {}

    private static final boolean DEBUG = Boolean.getBoolean("blackbird.debug.codegen");

    static ValueDeserializer<Object> tryGenerate(BeanDeserializerBase delegate,
            DeserializationContext ctxt,
            AnnotatedMethod buildMethod, boolean declaresViews,
            BeanReaderGenerator.Ignorals ignorals, Map<String, List<PropertyName>> aliases,
            boolean caseInsensitive) {
        try {
            ValueDeserializer<Object> codec = generate(delegate, ctxt, buildMethod,
                    declaresViews, ignorals, aliases, caseInsensitive);
            if (DEBUG) {
                System.err.println("bbdebug tryGenerate " + delegate.handledType().getName()
                        + " -> " + (codec == null ? "null (gated)" : codec.getClass().getName()));
            }
            return codec;
        } catch (Throwable t) {
            if (DEBUG) {
                System.err.println("bbdebug tryGenerate " + delegate.handledType().getName() + " threw:");
                t.printStackTrace();
            }
            CodegenFallbacks.generationFailure(delegate.handledType(), t);
            return null;
        }
    }

    private static ValueDeserializer<Object> generate(BeanDeserializerBase delegate,
            DeserializationContext ctxt,
            AnnotatedMethod buildMethod, boolean declaresViews,
            BeanReaderGenerator.Ignorals ignorals, Map<String, List<PropertyName>> aliases,
            boolean caseInsensitive)
            throws ReflectiveOperationException {
        // Deliberately no delegate.hasViews() gate: 3.x disables
        // DEFAULT_VIEW_INCLUSION by default, which marks every bean as needing
        // view processing; the generated codec instead checks
        // ctxt.getActiveView() per call and delegates when a view is active.
        if (delegate.getObjectIdReader(ctxt) != null) {
            if (DEBUG) System.err.println("bbdebug gate: objectId");
            return null;
        }
        Class<?> beanClass = delegate.handledType();
        if (Modifier.isAbstract(beanClass.getModifiers())) {
            if (DEBUG) System.err.println("bbdebug gate: abstract");
            return null;
        }
        if (buildMethod != null) {
            return generateBuilder(delegate, ctxt, beanClass, buildMethod, declaresViews,
                    ignorals, aliases, caseInsensitive);
        }
        if (beanClass.isRecord()) {
            return generateRecord(delegate, ctxt, beanClass, declaresViews, ignorals,
                    aliases, caseInsensitive);
        }
        if (!delegate.getValueInstantiator().canCreateUsingDefault()) {
            if (DEBUG) System.err.println("bbdebug gate: instantiator");
            return null;
        }
        // A constant constructor handle is only equivalent when the
        // instantiator's default creator IS the no-arg constructor. Everything
        // else that still reports canCreateUsingDefault - a no-arg
        // @JsonCreator factory, a custom instantiator, a constructor the
        // module lookup cannot unreflect - constructs through the stock
        // instantiator held in class data instead.
        MethodHandle defaultCtor = null;
        if (delegate.getValueInstantiator().getDefaultCreator()
                instanceof AnnotatedConstructor ac) {
            try {
                defaultCtor = MemberHandles.defaultConstructor(ac.getAnnotated());
            } catch (IllegalAccessException e) {
                if (DEBUG) System.err.println("bbdebug demote: ctor access, instantiator mode");
            }
        }

        List<GenProp> props = new ArrayList<>();
        List<Named> names = new ArrayList<>();
        for (Iterator<SettableBeanProperty> it = delegate.properties(); it.hasNext(); ) {
            SettableBeanProperty prop = it.next();
            if (prop instanceof CreatorProperty
                    || prop.getMetadata().getMergeInfo() != null) {
                if (DEBUG) System.err.println("bbdebug gate: prop " + prop.getName());
                return null;
            }
            props.add(classify(prop));
            names.add(Named.fromString(prop.getName()));
        }
        if (props.isEmpty()) {
            if (DEBUG) System.err.println("bbdebug gate: no props");
            return null;
        }
        int[] aliasArms = appendAliases(names, props, aliases);
        PropertyNameMatcher matcher = matcher(ctxt, names, caseInsensitive);
        return BeanReaderGenerator.generate(beanClass, props, matcher, delegate, defaultCtor,
                defaultCtor == null ? delegate.getValueInstantiator() : null,
                viewStrategy(ctxt, props, declaresViews), ignorals, aliasArms);
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

    // Builder-based beans: the stock ValueInstantiator creates the builder,
    // properties apply through normalized (Object, eV)Object setter handles,
    // and the build method - a constant (Object)Object handle - produces the
    // value.
    private static ValueDeserializer<Object> generateBuilder(BeanDeserializerBase delegate,
            DeserializationContext ctxt, Class<?> beanClass,
            AnnotatedMethod buildMethod, boolean declaresViews,
            BeanReaderGenerator.Ignorals ignorals, Map<String, List<PropertyName>> aliases,
            boolean caseInsensitive)
            throws ReflectiveOperationException {
        Method build = buildMethod.getAnnotated();
        Class<?> builderClass = build.getDeclaringClass();
        if (!delegate.getValueInstantiator().canCreateUsingDefault()
                || Modifier.isPrivate(builderClass.getModifiers())
                || build.getParameterCount() != 0) {
            if (DEBUG) System.err.println("bbdebug gate: builder shape");
            return null;
        }
        MethodHandle buildMH;
        try {
            buildMH = MemberHandles.unary(build);
        } catch (IllegalAccessException e) {
            if (DEBUG) System.err.println("bbdebug gate: build method access");
            return null;
        }
        List<GenProp> props = new ArrayList<>();
        List<Named> names = new ArrayList<>();
        for (Iterator<SettableBeanProperty> it = delegate.properties(); it.hasNext(); ) {
            SettableBeanProperty prop = it.next();
            if (prop instanceof CreatorProperty
                    || prop.getMetadata().getMergeInfo() != null) {
                if (DEBUG) System.err.println("bbdebug gate: builder prop " + prop.getName());
                return null;
            }
            props.add(classifyBuilder(prop, builderClass));
            names.add(Named.fromString(prop.getName()));
        }
        if (props.isEmpty()) {
            if (DEBUG) System.err.println("bbdebug gate: builder no props");
            return null;
        }
        int[] aliasArms = appendAliases(names, props, aliases);
        PropertyNameMatcher matcher = matcher(ctxt, names, caseInsensitive);
        return BeanReaderGenerator.generate(beanClass, props, matcher, delegate, null, null,
                new BeanReaderGenerator.BuilderSupport(delegate.getValueInstantiator(), buildMH),
                null, viewStrategy(ctxt, props, declaresViews), ignorals, aliasArms);
    }

    // Tier-A builder setters must return void or the builder class exactly
    // (the normalized handle keeps the fluent return as the new builder, which
    // is what stock deserializeSetAndReturn does with a non-null result).
    private static GenProp classifyBuilder(SettableBeanProperty prop, Class<?> builderClass) {
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
        ValueDeserializer<?> valueDeser = prop.getValueDeserializer();
        if (valueDeser instanceof GeneratedReaderBase child) {
            return new GenProp(prop.getName(), Kind.CHILD, prop, raw, child, handle);
        }
        Kind kind = scalarKind(raw);
        if (kind == null || valueDeser == null
                || !STOCK_SCALAR_DESERS.contains(valueDeser.getClass().getName())) {
            return stock(prop);
        }
        return new GenProp(prop.getName(), kind, prop, raw, null, handle);
    }

    // Records collect components into typed locals and construct through the
    // canonical constructor; every property must be a CreatorProperty whose
    // creator index and type line up with the record components.
    private static ValueDeserializer<Object> generateRecord(BeanDeserializerBase delegate,
            DeserializationContext ctxt, Class<?> beanClass,
            boolean declaresViews, BeanReaderGenerator.Ignorals ignorals,
            Map<String, List<PropertyName>> aliases, boolean caseInsensitive)
            throws ReflectiveOperationException {
        if (!delegate.getValueInstantiator().canCreateFromObjectWith()) {
            if (DEBUG) System.err.println("bbdebug gate: record instantiator");
            return null;
        }
        // The generated code constructs through the canonical constructor. A
        // @JsonCreator factory (or a custom instantiator) must construct
        // through the instantiator instead, so such records stay on the stock
        // path. An AnnotatedConstructor that also passes the per-component
        // index and type match below is the canonical constructor: a second
        // constructor with the same signature cannot exist.
        if (!(delegate.getValueInstantiator().getWithArgsCreator()
                instanceof AnnotatedConstructor ctor)) {
            if (DEBUG) System.err.println("bbdebug gate: record creator is not the constructor");
            return null;
        }
        RecordComponent[] comps = beanClass.getRecordComponents();
        // The seen-component mask in the generated code is one long.
        if (comps.length > 64) {
            if (DEBUG) System.err.println("bbdebug gate: record size");
            return null;
        }
        SettableBeanProperty[] byIndex = new SettableBeanProperty[comps.length];
        int count = 0;
        for (Iterator<SettableBeanProperty> it = delegate.properties(); it.hasNext(); ) {
            SettableBeanProperty prop = it.next();
            if (!(prop instanceof CreatorProperty)
                    || prop.getMetadata().getMergeInfo() != null
                    // A missing injectable component takes its value from the
                    // context, which the typed-locals loop does not model.
                    || prop.getInjectionDefinition() != null) {
                if (DEBUG) System.err.println("bbdebug gate: record prop " + prop.getName());
                return null;
            }
            int idx = prop.getCreatorIndex();
            if (idx < 0 || idx >= comps.length || byIndex[idx] != null
                    || prop.getType().getRawClass() != comps[idx].getType()) {
                if (DEBUG) System.err.println("bbdebug gate: record index " + prop.getName());
                return null;
            }
            byIndex[idx] = prop;
            count++;
        }
        if (count != comps.length || count == 0) {
            if (DEBUG) System.err.println("bbdebug gate: record count");
            return null;
        }
        MethodHandle recordCtor;
        try {
            recordCtor = MemberHandles.constructor(ctor.getAnnotated());
        } catch (IllegalAccessException e) {
            // The module lookup cannot reach the canonical constructor, so
            // databind could not open it either: demote, stock fails the same
            // way at first use.
            if (DEBUG) System.err.println("bbdebug gate: record ctor access");
            return null;
        }
        List<GenProp> props = new ArrayList<>(comps.length);
        List<Named> names = new ArrayList<>(comps.length);
        for (int i = 0; i < comps.length; i++) {
            Class<?> compType = comps[i].getType();
            SettableBeanProperty prop = byIndex[i];
            ValueDeserializer<?> valueDeser = prop.getValueDeserializer();
            if (valueDeser instanceof GeneratedReaderBase child) {
                props.add(new GenProp(prop.getName(), Kind.CHILD, prop, compType, child, null));
                names.add(Named.fromString(prop.getName()));
                continue;
            }
            Kind kind = scalarKind(compType);
            if (kind == null || valueDeser == null
                    || !STOCK_SCALAR_DESERS.contains(valueDeser.getClass().getName())) {
                kind = Kind.STOCK;
            }
            props.add(new GenProp(prop.getName(), kind, prop, compType));
            names.add(Named.fromString(prop.getName()));
        }
        int[] aliasArms = appendAliases(names, props, aliases);
        PropertyNameMatcher matcher = matcher(ctxt, names, caseInsensitive);
        return BeanReaderGenerator.generate(beanClass, props, matcher, delegate, recordCtor,
                viewStrategy(ctxt, props, declaresViews), ignorals, aliasArms);
    }

    // Appends alias entries after the primary names, each mapping back to its
    // primary arm - the same layout stock BeanPropertyMap.initMatcher builds -
    // and returns the arm index per alias entry.
    private static int[] appendAliases(List<Named> names, List<GenProp> props,
            Map<String, List<PropertyName>> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return null;
        }
        List<Integer> arms = new ArrayList<>();
        for (int i = 0; i < props.size(); i++) {
            List<PropertyName> propAliases = aliases.get(props.get(i).name());
            if (propAliases == null) {
                continue;
            }
            for (PropertyName alias : propAliases) {
                names.add(Named.fromString(alias.getSimpleName()));
                arms.add(i);
            }
        }
        if (arms.isEmpty()) {
            return null;
        }
        int[] out = new int[arms.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = arms.get(i);
        }
        return out;
    }

    // The matcher kind mirrors stock BeanPropertyMap.initMatcher: the
    // case-insensitive variant when the mapper enables
    // ACCEPT_CASE_INSENSITIVE_PROPERTIES, with the configured locale.
    private static PropertyNameMatcher matcher(DeserializationContext ctxt, List<Named> names,
            boolean caseInsensitive) {
        if (caseInsensitive) {
            return ctxt.tokenStreamFactory().constructCINameMatcher(names, true,
                    ctxt.getConfig().getLocale());
        }
        return ctxt.tokenStreamFactory().constructNameMatcher(names, true);
    }

    private static GenProp classify(SettableBeanProperty prop) {
        Class<?> raw = prop.getType().getRawClass();
        MethodHandle handle = storeHandle(prop, raw);
        if (handle == null) {
            return stock(prop);
        }
        ValueDeserializer<?> valueDeser = prop.getValueDeserializer();
        if (valueDeser instanceof GeneratedReaderBase child) {
            return new GenProp(prop.getName(), Kind.CHILD, prop, raw, child, handle);
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
        return new GenProp(prop.getName(), Kind.STOCK, prop);
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
