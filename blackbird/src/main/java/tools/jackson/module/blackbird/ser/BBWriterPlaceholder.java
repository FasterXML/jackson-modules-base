package tools.jackson.module.blackbird.ser;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.BeanSerializer;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.module.blackbird.codegen.CodegenDebug;

/**
 * Takes the place of a stock bean serializer from the modifier on: it is a
 * copy of that serializer (as a {@link BeanSerializer}), so other modifiers
 * and databind's own checks see a bean serializer, and it behaves as stock
 * until resolution. Resolving it resolves the stock state and generates the
 * writer, which contextualization then hands out.
 */
final class BBWriterPlaceholder extends BeanSerializer
{
    private volatile ValueSerializer<Object> _codec;

    BBWriterPlaceholder(BeanSerializerBase src) {
        super(src);
    }

    @Override
    public void resolve(SerializationContext ctxt) {
        CodegenDebug.log(() -> "writer resolve " + handledType().getName());
        super.resolve(ctxt);
        _codec = BBWriterFactory.tryGenerate(this, _typeId != null, ctxt);
    }

    // Contextualization is the stock one; when it leaves this serializer as
    // it is, the generated writer takes its place.
    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        ValueSerializer<?> contextual = super.createContextual(ctxt, property);
        ValueSerializer<Object> codec = _codec;
        return (contextual == this && codec != null) ? codec : contextual;
    }

    @Override
    public void serialize(Object value, JsonGenerator g, SerializationContext ctxt) {
        ValueSerializer<Object> codec = _codec;
        if (codec != null) {
            codec.serialize(value, g, ctxt);
        } else {
            super.serialize(value, g, ctxt);
        }
    }
}
