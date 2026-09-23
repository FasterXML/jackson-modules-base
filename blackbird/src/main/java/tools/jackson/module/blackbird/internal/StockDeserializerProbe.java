package tools.jackson.module.blackbird.internal;

import java.util.Set;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.SettableAnyProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.deser.impl.ObjectIdReader;
import tools.jackson.databind.deser.impl.ValueInjector;
import tools.jackson.databind.util.NameTransformer;

/**
 * Reads protected state off a resolved stock bean deserializer through the
 * copy constructor, the same way BBWriterFactory's suppression probe reads
 * BeanPropertyWriter: the probe's own inherited fields are readable. Never
 * used as a deserializer; every operation stubs out.
 */
final class StockDeserializerProbe extends BeanDeserializerBase
{
    private StockDeserializerProbe(BeanDeserializerBase src) {
        super(src);
    }

    static SettableAnyProperty anySetterOf(BeanDeserializerBase src) {
        return new StockDeserializerProbe(src)._anySetter;
    }

    static ValueInjector[] injectablesOf(BeanDeserializerBase src) {
        return new StockDeserializerProbe(src)._injectables;
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object deserializeFromObject(JsonParser p, DeserializationContext ctxt) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object _deserializeUsingPropertyBased(JsonParser p, DeserializationContext ctxt) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BeanDeserializerBase withObjectIdReader(ObjectIdReader oir) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BeanDeserializerBase withByNameInclusion(Set<String> ignorableProps,
            Set<String> includableProps) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BeanDeserializerBase withIgnoreAllUnknown(boolean ignoreUnknown) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ValueDeserializer<Object> unwrappingDeserializer(DeserializationContext ctxt,
            NameTransformer unwrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected BeanDeserializerBase asArrayDeserializer() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void initNameMatcher(DeserializationContext ctxt) {
        throw new UnsupportedOperationException();
    }
}
