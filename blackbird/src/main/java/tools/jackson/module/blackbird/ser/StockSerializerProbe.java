package tools.jackson.module.blackbird.ser;

import java.util.Set;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.databind.ser.impl.ObjectIdWriter;
import tools.jackson.databind.util.NameTransformer;

/**
 * Reads protected state off a resolved stock bean serializer through the copy
 * constructor, the same way SuppressionProbe reads BeanPropertyWriter and
 * StockDeserializerProbe reads BeanDeserializerBase. Never used as a
 * serializer; every operation stubs out.
 */
final class StockSerializerProbe extends BeanSerializerBase
{
    private StockSerializerProbe(BeanSerializerBase src) {
        super(src);
    }

    static AnnotatedMember typeIdOf(BeanSerializerBase src) {
        return new StockSerializerProbe(src)._typeId;
    }

    @Override
    public void serialize(Object bean, JsonGenerator gen, SerializationContext ctxt) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BeanSerializerBase withObjectIdWriter(ObjectIdWriter objectIdWriter) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected BeanSerializerBase withByNameInclusion(Set<String> toIgnore,
            Set<String> toInclude) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected BeanSerializerBase asArraySerializer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BeanSerializerBase withFilterId(Object filterId) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected BeanSerializerBase withProperties(BeanPropertyWriter[] properties,
            BeanPropertyWriter[] filteredProperties) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ValueSerializer<Object> unwrappingSerializer(NameTransformer unwrapper) {
        throw new UnsupportedOperationException();
    }
}
