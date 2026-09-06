package tools.jackson.module.blackbird.codegen;

import java.util.Collection;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.type.LogicalType;
import tools.jackson.databind.util.AccessPattern;

/**
 * Superclass for generated codecs: everything except the plain-object
 * deserialize loop forwards to the stock deserializer the codec replaced, so
 * typed (polymorphic) entry, null handling, and update-value reads keep stock
 * semantics.
 */
public abstract class GeneratedCodecBase extends ValueDeserializer<Object>
{
    protected final BeanDeserializerBase _fallback;

    protected GeneratedCodecBase(BeanDeserializerBase fallback) {
        _fallback = fallback;
    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt,
            TypeDeserializer typeDeserializer) {
        return _fallback.deserializeWithType(p, ctxt, typeDeserializer);
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt, Object intoValue) {
        return _fallback.deserialize(p, ctxt, intoValue);
    }

    @Override
    public Object getNullValue(DeserializationContext ctxt) {
        return _fallback.getNullValue(ctxt);
    }

    @Override
    public Object getEmptyValue(DeserializationContext ctxt) {
        return _fallback.getEmptyValue(ctxt);
    }

    @Override
    public Object getAbsentValue(DeserializationContext ctxt) {
        return _fallback.getAbsentValue(ctxt);
    }

    @Override
    public AccessPattern getNullAccessPattern() {
        return _fallback.getNullAccessPattern();
    }

    @Override
    public AccessPattern getEmptyAccessPattern() {
        return _fallback.getEmptyAccessPattern();
    }

    @Override
    public Collection<Object> getKnownPropertyNames() {
        return _fallback.getKnownPropertyNames();
    }

    @Override
    public SettableBeanProperty findBackReference(String refName) {
        return _fallback.findBackReference(refName);
    }

    @Override
    public Boolean supportsUpdate(DeserializationConfig config) {
        return _fallback.supportsUpdate(config);
    }

    @Override
    public Class<?> handledType() {
        return _fallback.handledType();
    }

    @Override
    public LogicalType logicalType() {
        return _fallback.logicalType();
    }

    @Override
    public boolean isCachable() {
        return _fallback.isCachable();
    }

    // Called by generated code when a property name was expected but the
    // stream holds something else; reports through the context the way the
    // stock deserializer does.
    protected final Object _unexpectedToken(JsonParser p, DeserializationContext ctxt) {
        return ctxt.handleUnexpectedToken(_fallback.handledType(), p);
    }
}
