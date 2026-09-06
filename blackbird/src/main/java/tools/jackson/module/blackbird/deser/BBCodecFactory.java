package tools.jackson.module.blackbird.deser;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
import tools.jackson.databind.introspect.AnnotatedMethod;
import tools.jackson.module.blackbird.codegen.BeanCodecGenerator.GenProp;
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
            Function<Class<?>, MethodHandles.Lookup> lookups) {
        try {
            ValueDeserializer<Object> codec = generate(delegate, ctxt, lookups);
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
            Function<Class<?>, MethodHandles.Lookup> lookups)
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
            props.add(classify(prop));
            names.add(Named.fromString(prop.getName()));
        }
        if (props.isEmpty()) {
            if (DEBUG) System.err.println("bbdebug gate: no props");
            return null;
        }
        PropertyNameMatcher matcher = ctxt.tokenStreamFactory().constructNameMatcher(names, true);
        return BeanCodecGenerator.generate(beanClass, props, matcher, delegate);
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
            Kind kind = scalarKind(paramTypes[i]);
            ValueDeserializer<?> valueDeser = prop.getValueDeserializer();
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

    private static GenProp classify(SettableBeanProperty prop) {
        Kind kind = scalarKind(prop.getType().getRawClass());
        if (kind == null) {
            return stock(prop);
        }
        ValueDeserializer<?> valueDeser = prop.getValueDeserializer();
        if (valueDeser == null
                || !STOCK_SCALAR_DESERS.contains(valueDeser.getClass().getName())) {
            return stock(prop);
        }
        if (!(prop.getMember() instanceof AnnotatedMethod am)) {
            return stock(prop);
        }
        Method setter = am.getAnnotated();
        if (setter == null || setter.getParameterCount() != 1
                || !Modifier.isPublic(setter.getModifiers())
                || !Modifier.isPublic(setter.getDeclaringClass().getModifiers())
                || setter.getParameterTypes()[0] != prop.getType().getRawClass()) {
            return stock(prop);
        }
        return new GenProp(prop.getName(), kind, setter, prop);
    }

    private static GenProp stock(SettableBeanProperty prop) {
        return new GenProp(prop.getName(), Kind.STOCK, null, prop);
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
