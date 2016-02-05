package com.fasterxml.jackson.module.afterburner.deser;

import java.io.IOException;
import java.lang.annotation.Annotation;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.core.io.NumberInput;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.*;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.util.ClassUtil;

/**
 * Base class for concrete type-specific {@link SettableBeanProperty}
 * implementations.
 */
abstract class OptimizedSettableBeanProperty<T extends OptimizedSettableBeanProperty<T>>
    extends SettableBeanProperty
{
    private static final long serialVersionUID = 1L; // since 2.5

    /**
     * We will need to keep the original instance handy as
     * some calls are best just delegated
     */
    protected final SettableBeanProperty _originalSettable;
    
    protected final BeanPropertyMutator _propertyMutator;
    protected final int _optimizedIndex;

    /*
    /********************************************************************** 
    /* Life-cycle
    /********************************************************************** 
     */

    public OptimizedSettableBeanProperty(SettableBeanProperty src,
            BeanPropertyMutator mutator, int index)
    {
        super(src);
        _originalSettable = src;
        _propertyMutator = mutator;
        _optimizedIndex = index;
    }

    protected OptimizedSettableBeanProperty(OptimizedSettableBeanProperty<T> src,
            JsonDeserializer<?> deser)
    {
        super(src, deser);
        _originalSettable = src._originalSettable.withValueDeserializer(deser);
        _propertyMutator = src._propertyMutator;
        _optimizedIndex = src._optimizedIndex;
    }

    protected OptimizedSettableBeanProperty(OptimizedSettableBeanProperty<T> src,
            PropertyName name)
    {
        super(src, name);
        _originalSettable = src._originalSettable.withName(name);
        _propertyMutator = src._propertyMutator;
        _optimizedIndex = src._optimizedIndex;
    }

    @Override
    public abstract SettableBeanProperty withValueDeserializer(JsonDeserializer<?> deser);

    public abstract SettableBeanProperty withMutator(BeanPropertyMutator mut);

    /*
    /********************************************************************** 
    /* Overridden getters
    /********************************************************************** 
     */

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> ann) {
        return _originalSettable.getAnnotation(ann);
    }

    @Override
    public AnnotatedMember getMember() {
        return _originalSettable.getMember();
    }
    
    /*
    /********************************************************************** 
    /* Deserialization, regular
    /********************************************************************** 
     */
    
    @Override
    public abstract void deserializeAndSet(JsonParser jp, DeserializationContext ctxt,
            Object arg2) throws IOException, JsonProcessingException;

    @Override
    public abstract void set(Object bean, Object value) throws IOException;

    /*
    /********************************************************************** 
    /* Deserialization, builders
    /********************************************************************** 
     */
    
    /* !!! 19-Feb-2012, tatu:
     * 
     * We do not yet generate code for these methods: it would not be hugely
     * difficult to add them, but let's first see if anyone needs them...
     * (it is non-trivial, adds code etc, so not without cost).
     * 
     * That is: we'll use Reflection fallback for Builder-based deserialization,
     * so it will not be significantly faster.
     */

    @Override
    public abstract Object deserializeSetAndReturn(JsonParser jp,
            DeserializationContext ctxt, Object instance) throws IOException;


    @Override
    public Object setAndReturn(Object instance, Object value) throws IOException {
        return _originalSettable.setAndReturn(instance, value);
    }

    /*
    /********************************************************************** 
    /* Extended API
    /********************************************************************** 
     */

    public SettableBeanProperty getOriginalProperty() {
        return _originalSettable;
    }

    public int getOptimizedIndex() {
        return _optimizedIndex;
    }

    /*
    /********************************************************************** 
    /* Helper methods
    /********************************************************************** 
     */

    protected final boolean _deserializeBoolean(JsonParser p, DeserializationContext ctxt) throws IOException
    {
        JsonToken t = p.getCurrentToken();
        if (t == JsonToken.VALUE_TRUE) return true;
        if (t == JsonToken.VALUE_FALSE) return false;
        if (t == JsonToken.VALUE_NULL) return false;

        if (t == JsonToken.VALUE_NUMBER_INT) {
            // 11-Jan-2012, tatus: May be outside of int...
            if (p.getNumberType() == NumberType.INT) {
                return (p.getIntValue() != 0);
            }
            return _deserializeBooleanFromOther(p, ctxt);
        }
        // And finally, let's allow Strings to be converted too
        if (t == JsonToken.VALUE_STRING) {
            String text = p.getText().trim();
            if ("true".equals(text) || "True".equals(text)) {
                return true;
            }
            if ("false".equals(text) || "False".equals(text) || text.length() == 0) {
                return false;
            }
            if (_hasTextualNull(text)) {
                return false;
            }
            throw ctxt.weirdStringException(text, Boolean.TYPE, "only \"true\" or \"false\" recognized");
        }
        // [databind#381]
        if (t == JsonToken.START_ARRAY && ctxt.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
            p.nextToken();
            final boolean parsed = _deserializeBooleanFromOther(p, ctxt);
            t = p.nextToken();
            if (t != JsonToken.END_ARRAY) {
                throw ctxt.wrongTokenException(p, JsonToken.END_ARRAY, 
                        "Attempted to unwrap single value array for single 'boolean' value but there was more than a single value in the array");
            }            
            return parsed;            
        }
        // Otherwise, no can do:
        throw ctxt.mappingException(Boolean.TYPE, t);
    }

    protected final short _deserializeShort(JsonParser jp, DeserializationContext ctxt)
        throws IOException
    {
        int value = _deserializeInt(jp, ctxt);
        // So far so good: but does it fit?
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw ctxt.weirdStringException(String.valueOf(value),
                    Short.TYPE, "overflow, value can not be represented as 16-bit value");
        }
        return (short) value;
    }

    protected final int _deserializeInt(JsonParser p, DeserializationContext ctxt)
        throws IOException
    {
        if (p.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return p.getIntValue();
        }
        JsonToken t = p.getCurrentToken();
        if (t == JsonToken.VALUE_STRING) { // let's do implicit re-parse
            String text = p.getText().trim();
            if (_hasTextualNull(text)) {
                return 0;
            }
            try {
                int len = text.length();
                if (len > 9) {
                    long l = Long.parseLong(text);
                    if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
                        throw ctxt.weirdStringException(text, Integer.TYPE,
                            "Overflow: numeric value ("+text+") out of range of int ("+Integer.MIN_VALUE+" - "+Integer.MAX_VALUE+")");
                    }
                    return (int) l;
                }
                if (len == 0) {
                    return 0;
                }
                return NumberInput.parseInt(text);
            } catch (IllegalArgumentException iae) {
                throw ctxt.weirdStringException(text, Integer.TYPE, "not a valid int value");
            }
        }
        if (t == JsonToken.VALUE_NUMBER_FLOAT) {
            if (!ctxt.isEnabled(DeserializationFeature.ACCEPT_FLOAT_AS_INT)) {
                _failDoubleToIntCoercion(p, ctxt, "int");
            }
            return p.getValueAsInt();
        }
        if (t == JsonToken.VALUE_NULL) {
            return 0;
        }
        if (t == JsonToken.START_ARRAY && ctxt.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
            p.nextToken();
            final int parsed = _deserializeInt(p, ctxt);
            t = p.nextToken();
            if (t != JsonToken.END_ARRAY) {
                throw ctxt.wrongTokenException(p, JsonToken.END_ARRAY, 
                        "Attempted to unwrap single value array for single 'int' value but there was more than a single value in the array");
            }            
            return parsed;            
        }
        // Otherwise, no can do:
        throw ctxt.mappingException(Integer.TYPE, t);
    }

    protected final long _deserializeLong(JsonParser p, DeserializationContext ctxt)
        throws IOException
    {
        switch (p.getCurrentTokenId()) {
        case JsonTokenId.ID_NUMBER_INT:
            return p.getLongValue();
        case JsonTokenId.ID_NUMBER_FLOAT:
            if (!ctxt.isEnabled(DeserializationFeature.ACCEPT_FLOAT_AS_INT)) {
                _failDoubleToIntCoercion(p, ctxt, "long");
            }
            return p.getValueAsLong();
        case JsonTokenId.ID_STRING:
            String text = p.getText().trim();
            if (text.length() == 0 || _hasTextualNull(text)) {
                return 0L;
            }
            try {
                return NumberInput.parseLong(text);
            } catch (IllegalArgumentException iae) { }
            throw ctxt.weirdStringException(text, Long.TYPE, "not a valid long value");
        case JsonTokenId.ID_NULL:
            return 0L;
        case JsonTokenId.ID_START_ARRAY:
            if (ctxt.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
                p.nextToken();
                final long parsed = _deserializeLong(p, ctxt);
                JsonToken t = p.nextToken();
                if (t != JsonToken.END_ARRAY) {
                    throw ctxt.wrongTokenException(p, JsonToken.END_ARRAY, 
                            "Attempted to unwrap single value array for single 'long' value but there was more than a single value in the array");
                }            
                return parsed;
            }
            break;
        }
        throw ctxt.mappingException(Long.TYPE, p.getCurrentToken());
    }

    protected final String _deserializeString(JsonParser p, DeserializationContext ctxt) throws IOException
    {
        switch (p.getCurrentTokenId()) {
        case JsonTokenId.ID_STRING:
            return p.getText();
        case JsonTokenId.ID_NULL:
            return null;
        case JsonTokenId.ID_START_ARRAY:
            if (ctxt.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
                p.nextToken();
                final String parsed = _deserializeString(p, ctxt);
                if (p.nextToken() != JsonToken.END_ARRAY) {
                    throw ctxt.wrongTokenException(p, JsonToken.END_ARRAY, 
                            "Attempted to unwrap single value array for single 'String' value but there was more than a single value in the array");
                }            
                return parsed;            
            }
            break;
        case JsonTokenId.ID_EMBEDDED_OBJECT:
            Object ob = p.getEmbeddedObject();
            if (ob == null) {
                return null;
            }
            if (ob instanceof byte[]) {
                return Base64Variants.getDefaultVariant().encode((byte[]) ob, false);
            }
            // otherwise, try conversion using toString()...
            return ob.toString();
        default:
            // allow coercions for other scalar types
            String text = p.getValueAsString();
            if (text != null) {
                return text;
            }
        }
        throw ctxt.mappingException(String.class, p.getCurrentToken());
    }

    protected final boolean _deserializeBooleanFromOther(JsonParser p, DeserializationContext ctxt)
        throws IOException
    {
        if (p.getNumberType() == NumberType.LONG) {
            return (p.getLongValue() == 0L) ? Boolean.FALSE : Boolean.TRUE;
        }
        // no really good logic; let's actually resort to textual comparison
        String str = p.getText();
        if ("0.0".equals(str) || "0".equals(str)) {
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    // // More helper methods from StdDeserializer
    
    protected void _failDoubleToIntCoercion(JsonParser p, DeserializationContext ctxt,
            String type) throws IOException
    {
        throw ctxt.mappingException("Can not coerce a floating-point value ('%s') into %s; enable `DeserializationFeature.ACCEPT_FLOAT_AS_INT` to allow",
                        p.getValueAsString(), type);
    }

    protected boolean _hasTextualNull(String value) {
        return "null".equals(value);
    }

    /**
     * Helper method used to check whether given serializer is the default
     * serializer implementation: this is necessary to avoid overriding other
     * kinds of deserializers.
     */
    protected boolean _isDefaultDeserializer(JsonDeserializer<?> deser) {
        return (deser == null) || ClassUtil.isJacksonStdImpl(deser);
    }
}
