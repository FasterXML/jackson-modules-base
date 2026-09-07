package tools.jackson.module.blackbird.codegen;

import java.util.Collection;
import java.util.Iterator;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.type.LogicalType;
import tools.jackson.databind.util.AccessPattern;
import tools.jackson.databind.util.NameTransformer;

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

    // Unwrapping must produce the stock unwrapping variant: the generated loop
    // reads one JSON object, which is not the shape an unwrapped value has.
    @Override
    public ValueDeserializer<Object> unwrappingDeserializer(DeserializationContext ctxt,
            NameTransformer unwrapper) {
        return _fallback.unwrappingDeserializer(ctxt, unwrapper);
    }

    // Called by generated code when a property name was expected but the
    // stream holds something else; reports through the context the way the
    // stock deserializer does.
    protected final Object _unexpectedToken(JsonParser p, DeserializationContext ctxt) {
        return ctxt.handleUnexpectedToken(_fallback.handledType(), p);
    }

    // Called by generated code for a name the matcher does not know: runs the
    // configured problem handlers and honors FAIL_ON_UNKNOWN_PROPERTIES, like
    // the stock loop. Beans with ignored or included property sets never
    // generate a codec, so plain unknown handling is the whole contract here.
    // beanOrBuilder is null in record mode, where no instance exists yet.
    protected final void _handleUnknown(JsonParser p, DeserializationContext ctxt,
            Object beanOrBuilder) {
        String name = p.currentName();
        p.nextToken();
        ctxt.handleUnknownProperty(p, _fallback,
                (beanOrBuilder == null) ? _fallback.handledType() : beanOrBuilder, name);
    }

    // Called by generated record codecs when the document ended with unseen
    // components; mirrors PropertyValueBuffer's required and
    // FAIL_ON_MISSING_CREATOR_PROPERTIES reporting.
    protected final void _checkRecordSeen(DeserializationContext ctxt, long seen, int count) {
        for (int i = 0; i < count; i++) {
            if ((seen & (1L << i)) != 0) {
                continue;
            }
            SettableBeanProperty prop = _creatorPropByIndex(i);
            if (prop == null) {
                continue;
            }
            if (prop.isRequired()) {
                ctxt.reportInputMismatch(prop,
                        "Missing required creator property '%s' (index %d)",
                        prop.getName(), i);
            }
            if (ctxt.isEnabled(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)) {
                ctxt.reportInputMismatch(prop,
                        "Missing creator property '%s' (index %d); `DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES` enabled",
                        prop.getName(), i);
            }
        }
    }

    private SettableBeanProperty _creatorPropByIndex(int index) {
        for (Iterator<SettableBeanProperty> it = _fallback.properties(); it.hasNext(); ) {
            SettableBeanProperty prop = it.next();
            if (prop.getCreatorIndex() == index) {
                return prop;
            }
        }
        return null;
    }
}
