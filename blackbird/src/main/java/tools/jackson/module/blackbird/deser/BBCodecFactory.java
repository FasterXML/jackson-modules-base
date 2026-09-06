package tools.jackson.module.blackbird.deser;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

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
            DeserializationContext ctxt) {
        try {
            ValueDeserializer<Object> codec = generate(delegate, ctxt);
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
            DeserializationContext ctxt) throws ReflectiveOperationException {
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
