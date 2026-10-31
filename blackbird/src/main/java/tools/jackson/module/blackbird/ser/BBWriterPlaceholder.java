package tools.jackson.module.blackbird.ser;

import com.fasterxml.jackson.annotation.JsonFormat;

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
 * writer, which contextualization then hands out. When no writer is
 * generated, contextualization hands out the stock serializer instead, so
 * a demoted bean costs nothing over stock.
 */
final class BBWriterPlaceholder extends BeanSerializer
{
    // The serializer this copies (BeanSerializer or UnrolledBeanSerializer).
    // The copy shares its property writers, and resolution only assigns
    // serializers to those writers, so resolving this resolves the original
    // serializer as well.
    private final BeanSerializerBase _original;

    private volatile ValueSerializer<Object> _codec;

    // Set (to _original) once resolution generated no writer
    private volatile BeanSerializerBase _stock;

    BBWriterPlaceholder(BeanSerializerBase src) {
        super(src);
        _original = src;
    }

    @Override
    public void resolve(SerializationContext ctxt) {
        CodegenDebug.log(() -> "writer resolve " + handledType().getName());
        super.resolve(ctxt);
        ValueSerializer<Object> codec = BBWriterFactory.tryGenerate(this, _props, _filteredProps,
                _typeId != null, _serializationShape == JsonFormat.Shape.ARRAY, ctxt);
        _codec = codec;
        if (codec == null) {
            _stock = _original;
        }
    }

    // Contextualization is the stock one, on this resolved instance; when it
    // leaves this serializer as it is, the generated writer (or, when
    // demoted, the original serializer) takes its place.
    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        ValueSerializer<?> contextual = super.createContextual(ctxt, property);
        if (contextual != this) {
            return contextual;
        }
        ValueSerializer<Object> codec = _codec;
        if (codec != null) {
            return codec;
        }
        BeanSerializerBase stock = _stock;
        return (stock != null) ? stock : this;
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
