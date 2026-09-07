package tools.jackson.module.blackbird.deser;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.core.util.Named;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.CreatorProperty;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.introspect.AnnotatedField;
import tools.jackson.databind.introspect.AnnotatedMethod;
import tools.jackson.module.blackbird.codegen.BeanCodecGenerator.GenProp;
import tools.jackson.module.blackbird.codegen.GeneratedCodecBase;
import tools.jackson.module.blackbird.codegen.BeanCodecGenerator.Kind;
import tools.jackson.module.blackbird.codegen.BeanCodecGenerator;

/**
 * Builds a generated codec from a resolved stock bean deserializer, or returns
 * null when the bean does not qualify. Qualification is conservative: every
 * gate that cannot be verified cheaply demotes a property to the stock path or
 * rejects the bean entirely, so semantics never drift from databind.
 */
final class BBCodecFactory
{
    private static final Set<String> STOCK_SCALAR_DESERS = Set.of(
            "tools.jackson.databind.deser.jdk.StringDeserializer",
            "tools.jackson.databind.deser.jdk.NumberDeserializers$IntegerDeserializer",
            "tools.jackson.databind.deser.jdk.NumberDeserializers$LongDeserializer",
            "tools.jackson.databind.deser.jdk.NumberDeserializers$BooleanDeserializer");

    private BBCodecFactory() {}

    private static final boolean DEBUG = Boolean.getBoolean("blackbird.debug.codegen");

    static ValueDeserializer<Object> tryGenerate(BeanDeserializerBase delegate,
            DeserializationContext ctxt,
            Function<Class<?>, MethodHandles.Lookup> lookups,
            AnnotatedMethod buildMethod) {
        try {
            ValueDeserializer<Object> codec = generate(delegate, ctxt, lookups, buildMethod);
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
            return null;
        }
    }

    private static ValueDeserializer<Object> generate(BeanDeserializerBase delegate,
            DeserializationContext ctxt,
            Function<Class<?>, MethodHandles.Lookup> lookups,
            AnnotatedMethod buildMethod)
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
        if (!Modifier.isPublic(beanClass.getModifiers())
                || Modifier.isAbstract(beanClass.getModifiers())) {
            if (DEBUG) System.err.println("bbdebug gate: class modifiers");
            return null;
        }
        // Generated code refers to the bean class by name, which the hidden
        // class resolves through this module's loader. A bean from a foreign
        // classloader would resolve to a different (or no) class, so it stays
        // on the stock path.
        if (!visibleToGenerator(beanClass)) {
            if (DEBUG) System.err.println("bbdebug gate: foreign classloader");
            return null;
        }
        if (buildMethod != null) {
            return generateBuilder(delegate, ctxt, beanClass, lookups, buildMethod);
        }
        if (beanClass.isRecord()) {
            return generateRecord(delegate, ctxt, beanClass, lookups);
        }
        if (!delegate.getValueInstantiator().canCreateUsingDefault()) {
            if (DEBUG) System.err.println("bbdebug gate: instantiator");
            return null;
        }
        if (!Modifier.isPublic(beanClass.getConstructor().getModifiers())) {
            if (DEBUG) System.err.println("bbdebug gate: ctor modifiers");
            return null;
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
            props.add(classify(prop, beanClass, lookups));
            names.add(Named.fromString(prop.getName()));
        }
        if (props.isEmpty()) {
            if (DEBUG) System.err.println("bbdebug gate: no props");
            return null;
        }
        PropertyNameMatcher matcher = ctxt.tokenStreamFactory().constructNameMatcher(names, true);
        return BeanCodecGenerator.generate(beanClass, props, matcher, delegate);
    }

    // Builder-based beans: the stock ValueInstantiator creates the builder,
    // properties apply to it (tier-A fluent setters must return void or the
    // builder class exactly, or they demote to the stock path), and the build
    // method - unreflected through the user lookup and asType'd to
    // (Object)Object - produces the value.
    private static ValueDeserializer<Object> generateBuilder(BeanDeserializerBase delegate,
            DeserializationContext ctxt, Class<?> beanClass,
            Function<Class<?>, MethodHandles.Lookup> lookups, AnnotatedMethod buildMethod)
            throws ReflectiveOperationException {
        Method build = buildMethod.getAnnotated();
        Class<?> builderClass = build.getDeclaringClass();
        if (!delegate.getValueInstantiator().canCreateUsingDefault()
                || !Modifier.isPublic(builderClass.getModifiers())
                || !visibleToGenerator(builderClass)
                || !Modifier.isPublic(build.getModifiers())
                || build.getParameterCount() != 0) {
            if (DEBUG) System.err.println("bbdebug gate: builder shape");
            return null;
        }
        MethodHandles.Lookup lookup = lookups.apply(builderClass);
        if (lookup == null) {
            if (DEBUG) System.err.println("bbdebug gate: builder lookup");
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
        MethodHandle buildMH = lookup.unreflect(build)
                .asType(MethodType.methodType(Object.class, Object.class));
        PropertyNameMatcher matcher = ctxt.tokenStreamFactory().constructNameMatcher(names, true);
        return BeanCodecGenerator.generate(beanClass, props, matcher, delegate, null,
                new BeanCodecGenerator.BuilderSupport(
                        delegate.getValueInstantiator(), buildMH, builderClass));
    }

    private static GenProp classifyBuilder(SettableBeanProperty prop, Class<?> builderClass) {
        Class<?> raw = prop.getType().getRawClass();
        Method setter = setterOf(prop, raw);
        if (setter == null || !Modifier.isPublic(setter.getModifiers())
                || setter.getDeclaringClass() != builderClass
                || (setter.getReturnType() != void.class
                        && setter.getReturnType() != builderClass)) {
            return stock(prop);
        }
        ValueDeserializer<?> valueDeser = prop.getValueDeserializer();
        if (valueDeser instanceof GeneratedCodecBase child) {
            return new GenProp(prop.getName(), Kind.CHILD, setter, prop, raw, child, null);
        }
        Kind kind = scalarKind(raw);
        if (kind == null || valueDeser == null
                || !STOCK_SCALAR_DESERS.contains(valueDeser.getClass().getName())) {
            return stock(prop);
        }
        return new GenProp(prop.getName(), kind, setter, prop);
    }

    // Records collect components into typed locals and construct through the
    // canonical constructor; every property must be a CreatorProperty whose
    // creator index and type line up with the record components.
    private static ValueDeserializer<Object> generateRecord(BeanDeserializerBase delegate,
            DeserializationContext ctxt, Class<?> beanClass,
            Function<Class<?>, MethodHandles.Lookup> lookups)
            throws ReflectiveOperationException {
        if (!delegate.getValueInstantiator().canCreateFromObjectWith()) {
            if (DEBUG) System.err.println("bbdebug gate: record instantiator");
            return null;
        }
        RecordComponent[] comps = beanClass.getRecordComponents();
        SettableBeanProperty[] byIndex = new SettableBeanProperty[comps.length];
        int count = 0;
        for (Iterator<SettableBeanProperty> it = delegate.properties(); it.hasNext(); ) {
            SettableBeanProperty prop = it.next();
            if (!(prop instanceof CreatorProperty)
                    || prop.getMetadata().getMergeInfo() != null) {
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
        MethodHandles.Lookup lookup = lookups.apply(beanClass);
        if (lookup == null) {
            if (DEBUG) System.err.println("bbdebug gate: record lookup");
            return null;
        }
        Class<?>[] paramTypes = new Class<?>[comps.length];
        List<GenProp> props = new ArrayList<>(comps.length);
        List<Named> names = new ArrayList<>(comps.length);
        for (int i = 0; i < comps.length; i++) {
            paramTypes[i] = comps[i].getType();
            SettableBeanProperty prop = byIndex[i];
            ValueDeserializer<?> valueDeser = prop.getValueDeserializer();
            if (valueDeser instanceof GeneratedCodecBase child) {
                props.add(new GenProp(prop.getName(), Kind.CHILD, null, prop,
                        paramTypes[i], child, null));
                names.add(Named.fromString(prop.getName()));
                continue;
            }
            Kind kind = scalarKind(paramTypes[i]);
            if (kind == null || valueDeser == null
                    || !STOCK_SCALAR_DESERS.contains(valueDeser.getClass().getName())) {
                kind = Kind.STOCK;
            }
            props.add(new GenProp(prop.getName(), kind, null, prop, paramTypes[i]));
            names.add(Named.fromString(prop.getName()));
        }
        MethodHandle recordCtor = lookup.findConstructor(beanClass,
                MethodType.methodType(void.class, paramTypes));
        PropertyNameMatcher matcher = ctxt.tokenStreamFactory().constructNameMatcher(names, true);
        return BeanCodecGenerator.generate(beanClass, props, matcher, delegate, recordCtor);
    }

    private static GenProp classify(SettableBeanProperty prop, Class<?> beanClass,
            Function<Class<?>, MethodHandles.Lookup> lookups) {
        Class<?> raw = prop.getType().getRawClass();
        ValueDeserializer<?> valueDeser = prop.getValueDeserializer();
        Method setter = setterOf(prop, raw);
        if (setter != null) {
            return classifySetter(prop, beanClass, lookups, raw, valueDeser, setter);
        }
        Field field = fieldOf(prop, raw);
        if (field != null) {
            return classifyField(prop, beanClass, lookups, raw, valueDeser, field);
        }
        return stock(prop);
    }

    private static GenProp classifySetter(SettableBeanProperty prop, Class<?> beanClass,
            Function<Class<?>, MethodHandles.Lookup> lookups, Class<?> raw,
            ValueDeserializer<?> valueDeser, Method setter) {
        if (valueDeser instanceof GeneratedCodecBase child
                && Modifier.isPublic(setter.getModifiers())
                && Modifier.isPublic(setter.getDeclaringClass().getModifiers())) {
            return new GenProp(prop.getName(), Kind.CHILD, setter, prop, raw, child, null);
        }
        Kind kind = scalarKind(raw);
        if (kind == null || valueDeser == null
                || !STOCK_SCALAR_DESERS.contains(valueDeser.getClass().getName())) {
            return stock(prop);
        }
        if (Modifier.isPublic(setter.getModifiers())
                && Modifier.isPublic(setter.getDeclaringClass().getModifiers())) {
            return new GenProp(prop.getName(), kind, setter, prop);
        }
        // Non-public setter: reach it through the user-supplied lookup; any
        // access failure keeps the property on the stock path.
        try {
            MethodHandles.Lookup lookup = lookups.apply(beanClass);
            if (lookup == null) {
                return stock(prop);
            }
            MethodHandle mh = lookup.unreflect(setter)
                    .asType(MethodType.methodType(void.class, beanClass, raw));
            return new GenProp(prop.getName(), kind, null, prop, raw, null, mh);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return stock(prop);
        }
    }

    // Field-backed properties store through a generated putfield (public,
    // non-final field on a public declaring class) or, for a non-public field,
    // an unreflected setter handle reached through the user lookup - the same
    // fallback the non-public-setter path uses. Final fields stay on the stock
    // path so their (stock-defined) behavior is preserved exactly.
    private static GenProp classifyField(SettableBeanProperty prop, Class<?> beanClass,
            Function<Class<?>, MethodHandles.Lookup> lookups, Class<?> raw,
            ValueDeserializer<?> valueDeser, Field field) {
        if (Modifier.isFinal(field.getModifiers())) {
            return stock(prop);
        }
        boolean direct = Modifier.isPublic(field.getModifiers())
                && Modifier.isPublic(field.getDeclaringClass().getModifiers());
        if (valueDeser instanceof GeneratedCodecBase child && direct) {
            return new GenProp(prop.getName(), Kind.CHILD, null, prop, raw, child, null, field);
        }
        Kind kind = scalarKind(raw);
        if (kind == null || valueDeser == null
                || !STOCK_SCALAR_DESERS.contains(valueDeser.getClass().getName())) {
            return stock(prop);
        }
        if (direct) {
            return new GenProp(prop.getName(), kind, null, prop, raw, null, null, field);
        }
        try {
            MethodHandles.Lookup lookup = lookups.apply(beanClass);
            if (lookup == null) {
                return stock(prop);
            }
            MethodHandle mh = lookup.unreflectSetter(field)
                    .asType(MethodType.methodType(void.class, beanClass, raw));
            return new GenProp(prop.getName(), kind, null, prop, raw, null, mh);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return stock(prop);
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
        return new GenProp(prop.getName(), Kind.STOCK, null, prop);
    }

    static boolean visibleToGenerator(Class<?> cls) {
        try {
            return Class.forName(cls.getName(), false,
                    GeneratedCodecBase.class.getClassLoader()) == cls;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
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
