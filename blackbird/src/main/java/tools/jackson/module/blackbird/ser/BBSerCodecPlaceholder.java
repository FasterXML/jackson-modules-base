package tools.jackson.module.blackbird.ser;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.bean.BeanSerializerBase;

/**
 * Stands in for a stock bean serializer until resolution, then hands out the
 * generated writer through contextualization; anything that keeps the
 * placeholder still works through delegation.
 */
final class BBSerCodecPlaceholder extends ValueSerializer<Object>
{
    private final BeanSerializerBase _delegate;

    private volatile ValueSerializer<Object> _codec;

    BBSerCodecPlaceholder(BeanSerializerBase delegate) {
        _delegate = delegate;
    }

    @Override
    public void resolve(SerializationContext ctxt) {
        _delegate.resolve(ctxt);
        _codec = BBSerCodecFactory.tryGenerate(_delegate, ctxt);
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        ValueSerializer<?> contextual = _delegate.createContextual(ctxt, property);
        if (contextual != _delegate) {
            return contextual;
        }
        ValueSerializer<Object> codec = _codec;
        return (codec != null) ? codec : this;
    }

    @Override
    public void serialize(Object value, JsonGenerator g, SerializationContext ctxt) {
        ValueSerializer<Object> codec = _codec;
        if (codec != null) {
            codec.serialize(value, g, ctxt);
        } else {
            _delegate.serialize(value, g, ctxt);
        }
    }

    @Override
    public void serializeWithType(Object value, JsonGenerator g, SerializationContext ctxt,
            TypeSerializer typeSer) {
        _delegate.serializeWithType(value, g, ctxt, typeSer);
    }

    @Override
    public boolean isEmpty(SerializationContext ctxt, Object value) {
        return _delegate.isEmpty(ctxt, value);
    }

    @Override
    public boolean usesObjectId() {
        return _delegate.usesObjectId();
    }

    @Override
    public Class<?> handledType() {
        return _delegate.handledType();
    }
}
