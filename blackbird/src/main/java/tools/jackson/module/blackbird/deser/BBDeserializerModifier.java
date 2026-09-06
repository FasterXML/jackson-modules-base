package tools.jackson.module.blackbird.deser;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Modifier;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.deser.bean.BeanDeserializer;
import tools.jackson.databind.introspect.BeanPropertyDefinition;

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
    private final UnaryOperator<MethodHandles.Lookup> _accessGrant;

    public BBDeserializerModifier(Function<Class<?>, MethodHandles.Lookup> lookups,
            UnaryOperator<MethodHandles.Lookup> accessGrant) {
        _lookups = lookups;
        _accessGrant = accessGrant;
    }

    @Override
    public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config,
            BeanDescription.Supplier beanDescRef, ValueDeserializer<?> deserializer)
    {
        if (deserializer.getClass() != BeanDeserializer.class) {
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
        if (!Modifier.isPublic(beanClass.getModifiers())
                || Modifier.isAbstract(beanClass.getModifiers())
                || (beanClass.getEnclosingClass() != null
                        && !Modifier.isStatic(beanClass.getModifiers()))) {
            return deserializer;
        }
        try {
            if (!Modifier.isPublic(beanClass.getConstructor().getModifiers())) {
                return deserializer;
            }
        } catch (NoSuchMethodException e) {
            return deserializer;
        }
        if (beanDesc.findAnySetterAccessor() != null) {
            return deserializer;
        }
        for (BeanPropertyDefinition def : beanDesc.findProperties()) {
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
        return new BBCodecPlaceholder((BeanDeserializer) deserializer);
    }
}
