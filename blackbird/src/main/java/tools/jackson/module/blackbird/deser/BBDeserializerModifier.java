package tools.jackson.module.blackbird.deser;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.deser.BeanDeserializerBuilder;
import tools.jackson.databind.deser.bean.BeanDeserializer;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.deser.bean.BuilderBasedDeserializer;
import tools.jackson.databind.introspect.AnnotatedMethod;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.module.blackbird.codegen.CodegenFallbacks;

/**
 * Wraps eligible stock bean deserializers in a placeholder that generates a
 * per-bean codec at resolve time (ClassFile API hidden class). Every gate that
 * fails leaves the stock deserializer in place, so behavior never changes for
 * beans the generator does not fully understand.
 */
public class BBDeserializerModifier extends ValueDeserializerModifier
{
    private static final long serialVersionUID = 1L;

    // Reserved for member access beyond public API (private setters, creators
    // in non-exported packages); the v1 generator only touches public members.
    private final Function<Class<?>, MethodHandles.Lookup> _lookups;

    // The build method is only reachable from updateBuilder; the factory calls
    // updateBuilder and modifyDeserializer for the same bean back to back on
    // one thread, so a ThreadLocal hands it across. Not final: readObject
    // recreates it, since transient fields deserialize as null.
    private transient ThreadLocal<AnnotatedMethod> _pendingBuildMethod = new ThreadLocal<>();

    public BBDeserializerModifier(Function<Class<?>, MethodHandles.Lookup> lookups) {
        _lookups = lookups;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        _pendingBuildMethod = new ThreadLocal<>();
    }

    @Override
    public BeanDeserializerBuilder updateBuilder(DeserializationConfig config,
            BeanDescription.Supplier beanDescRef, BeanDeserializerBuilder builder) {
        _pendingBuildMethod.set(builder.getBuildMethod());
        return builder;
    }

    @Override
    public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config,
            BeanDescription.Supplier beanDescRef, ValueDeserializer<?> deserializer)
    {
        // Gate failures of any kind leave the stock deserializer in place. The
        // reflective gates can throw for exotic classes (a bean from a foreign
        // classloader with inconsistent InnerClasses metadata raises
        // IncompatibleClassChangeError from getEnclosingClass), and an
        // acceleration modifier must never break a bean stock databind handles.
        try {
            return doModify(config, beanDescRef, deserializer);
        } catch (RuntimeException | LinkageError e) {
            CodegenFallbacks.gateFailure(beanDescRef.getBeanClass(), e);
            return deserializer;
        }
    }

    private ValueDeserializer<?> doModify(DeserializationConfig config,
            BeanDescription.Supplier beanDescRef, ValueDeserializer<?> deserializer)
    {
        AnnotatedMethod buildMethod = _pendingBuildMethod.get();
        _pendingBuildMethod.remove();
        boolean builderBased = deserializer.getClass() == BuilderBasedDeserializer.class
                && buildMethod != null;
        if (!builderBased && deserializer.getClass() != BeanDeserializer.class) {
            return deserializer;
        }
        if (config.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                || config.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)) {
            return deserializer;
        }
        if (Boolean.TRUE.equals(config.getDefaultMergeable())) {
            return deserializer;
        }
        BeanDescription beanDesc = beanDescRef.get();
        Class<?> beanClass = beanDesc.getBeanClass();
        if (Modifier.isPrivate(beanClass.getModifiers())
                || (beanClass.getEnclosingClass() != null
                        && !Modifier.isStatic(beanClass.getModifiers()))) {
            return deserializer;
        }
        if (builderBased || beanClass.isRecord()) {
            if (config.isEnabled(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                    || config.isEnabled(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)) {
                return deserializer;
            }
        } else if (Modifier.isAbstract(beanClass.getModifiers())) {
            return deserializer;
        } else {
            try {
                if (Modifier.isPrivate(beanClass.getDeclaredConstructor().getModifiers())) {
                    return deserializer;
                }
            } catch (NoSuchMethodException e) {
                return deserializer;
            }
        }
        if (beanDesc.findAnySetterAccessor() != null) {
            return deserializer;
        }
        // Injected values arrive outside the property loop, which the
        // generated codec does not model.
        Map<Object, ?> injectables = beanDesc.findInjectables();
        if (injectables != null && !injectables.isEmpty()) {
            return deserializer;
        }
        // Ignored or included property sets change how unknown names are
        // handled (silently skipped vs reported); the codec's unknown arm
        // implements only the plain contract, so such beans stay stock.
        JsonIgnoreProperties.Value ignorals =
                config.getDefaultPropertyIgnorals(beanClass, beanDesc.getClassInfo());
        if (ignorals != null
                && (ignorals.getIgnoreUnknown() || !ignorals.getIgnored().isEmpty())) {
            return deserializer;
        }
        JsonIncludeProperties.Value inclusions =
                config.getDefaultPropertyInclusions(beanClass, beanDesc.getClassInfo());
        if (inclusions != null && inclusions.getIncluded() != null) {
            return deserializer;
        }
        if (!beanDesc.getIgnoredPropertyNames().isEmpty()) {
            return deserializer;
        }
        boolean declaresViews = config.getAnnotationIntrospector()
                .findViews(config, beanDesc.getClassInfo()) != null;
        for (BeanPropertyDefinition def : beanDesc.findProperties()) {
            if (def.findViews() != null) {
                declaresViews = true;
            }
            if (!def.findAliases().isEmpty()) {
                return deserializer;
            }
            if (def.getPrimaryMember() != null
                    && config.getAnnotationIntrospector()
                            .findUnwrappingNameTransformer(config, def.getPrimaryMember()) != null) {
                return deserializer;
            }
            if (def.getMetadata() != null && def.getMetadata().getMergeInfo() != null) {
                return deserializer;
            }
        }
        return new BBCodecPlaceholder((BeanDeserializerBase) deserializer, _lookups,
                builderBased ? buildMethod : null, declaresViews);
    }
}
