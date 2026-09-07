package tools.jackson.module.blackbird.deser;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.function.Function;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.introspect.AnnotatedMethod;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.type.LogicalType;
import tools.jackson.databind.util.AccessPattern;
import tools.jackson.databind.util.NameTransformer;

/**
 * Stands in for a stock bean deserializer until resolution: the codec needs
 * the resolved property list and the context's TokenStreamFactory, both of
 * which exist only at resolve time. After resolve, contextualization hands out
 * the generated codec directly, so steady-state call sites reference the codec
 * with no extra hop; anything that keeps the placeholder still works through
 * delegation.
 */
final class BBCodecPlaceholder extends ValueDeserializer<Object>
{
    private final BeanDeserializerBase _delegate;

    private final Function<Class<?>, MethodHandles.Lookup> _lookups;

    private final AnnotatedMethod _buildMethod;

    private volatile ValueDeserializer<Object> _codec;

    BBCodecPlaceholder(BeanDeserializerBase delegate,
            Function<Class<?>, MethodHandles.Lookup> lookups,
            AnnotatedMethod buildMethod) {
        _delegate = delegate;
        _lookups = lookups;
        _buildMethod = buildMethod;
    }

    @Override
    public void resolve(DeserializationContext ctxt) {
        if (Boolean.getBoolean("blackbird.debug.codegen")) {
            System.err.println("bbdebug resolve " + _delegate.handledType().getName());
        }
        _delegate.resolve(ctxt);
        _codec = BBCodecFactory.tryGenerate(_delegate, ctxt, _lookups, _buildMethod);
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        ValueDeserializer<?> contextual = _delegate.createContextual(ctxt, property);
        if (contextual != _delegate) {
            return contextual;
        }
        ValueDeserializer<Object> codec = _codec;
        return (codec != null) ? codec : this;
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) {
        ValueDeserializer<Object> codec = _codec;
        return (codec != null) ? codec.deserialize(p, ctxt) : _delegate.deserialize(p, ctxt);
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt, Object intoValue) {
        return _delegate.deserialize(p, ctxt, intoValue);
    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt,
            TypeDeserializer typeDeserializer) {
        return _delegate.deserializeWithType(p, ctxt, typeDeserializer);
    }

    @Override
    public Object getNullValue(DeserializationContext ctxt) {
        return _delegate.getNullValue(ctxt);
    }

    @Override
    public Object getEmptyValue(DeserializationContext ctxt) {
        return _delegate.getEmptyValue(ctxt);
    }

    @Override
    public Object getAbsentValue(DeserializationContext ctxt) {
        return _delegate.getAbsentValue(ctxt);
    }

    @Override
    public AccessPattern getNullAccessPattern() {
        return _delegate.getNullAccessPattern();
    }

    @Override
    public AccessPattern getEmptyAccessPattern() {
        return _delegate.getEmptyAccessPattern();
    }

    @Override
    public Collection<Object> getKnownPropertyNames() {
        return _delegate.getKnownPropertyNames();
    }

    @Override
    public SettableBeanProperty findBackReference(String refName) {
        return _delegate.findBackReference(refName);
    }

    @Override
    public Boolean supportsUpdate(DeserializationConfig config) {
        return _delegate.supportsUpdate(config);
    }

    @Override
    public Class<?> handledType() {
        return _delegate.handledType();
    }

    @Override
    public LogicalType logicalType() {
        return _delegate.logicalType();
    }

    @Override
    public boolean isCachable() {
        return _delegate.isCachable();
    }

    // Unwrapping must produce the stock unwrapping variant, never the codec
    // (rationale in GeneratedCodecBase.unwrappingDeserializer).
    @Override
    public ValueDeserializer<Object> unwrappingDeserializer(DeserializationContext ctxt,
            NameTransformer unwrapper) {
        return _delegate.unwrappingDeserializer(ctxt, unwrapper);
    }
}
