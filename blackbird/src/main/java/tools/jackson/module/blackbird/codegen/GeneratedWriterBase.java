package tools.jackson.module.blackbird.codegen;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.bean.BeanSerializerBase;

/**
 * Superclass for generated serializers: everything except the plain-object
 * serialize path forwards to the stock serializer the codec replaced.
 */
public abstract class GeneratedWriterBase extends ValueSerializer<Object>
{
    protected final BeanSerializerBase _fallback;

    protected GeneratedWriterBase(BeanSerializerBase fallback) {
        _fallback = fallback;
    }

    @Override
    public void serializeWithType(Object value, JsonGenerator g, SerializationContext ctxt,
            TypeSerializer typeSer) {
        _fallback.serializeWithType(value, g, ctxt, typeSer);
    }

    @Override
    public boolean isEmpty(SerializationContext ctxt, Object value) {
        return _fallback.isEmpty(ctxt, value);
    }

    @Override
    public boolean usesObjectId() {
        return _fallback.usesObjectId();
    }

    @Override
    public Class<?> handledType() {
        return _fallback.handledType();
    }
}
