package com.fasterxml.jackson.module.afterburner.deser;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.core.io.NumberInput;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.*;
import com.fasterxml.jackson.databind.deser.impl.NullsConstantProvider;
import com.fasterxml.jackson.databind.util.ClassUtil;

/**
 * Base class for concrete type-specific {@link SettableBeanProperty}
 * implementations.
 */
abstract class OptimizedSettableBeanProperty<T extends OptimizedSettableBeanProperty<T>>
    extends SettableBeanProperty.Delegating
{
    private static final long serialVersionUID = 1L;

    protected BeanPropertyMutator _propertyMutator;

    protected final int _optimizedIndex;

    /**
     * @since 2.9
     */
    final protected boolean _skipNulls;

    /**
     * Marker that we set if mutator turns out to be broken in a systemic
     * way that we can handle by redirecting it back to standard one.
     */
    private volatile boolean broken = false;

    /*
    /********************************************************************** 
    /* Life-cycle
    /********************************************************************** 
     */

    protected OptimizedSettableBeanProperty(SettableBeanProperty src,
            BeanPropertyMutator mutator, int index)
    {
        super(src);
        _propertyMutator = mutator;
        _optimizedIndex = index;
        _skipNulls = NullsConstantProvider.isSkipper(_nullProvider);
    }

    // Base impl of `withName()` fine as-is:
    // public SettableBeanProperty withName(PropertyName name);

    // But value deserializer handling needs some more care
    @Override
    public final SettableBeanProperty withValueDeserializer(JsonDeserializer<?> deser) {
        SettableBeanProperty newDelegate = delegate.withValueDeserializer(deser);
        if (newDelegate == delegate) {
            return this;
        }
        if (!_isDefaultDeserializer(deser)) {
            return newDelegate;
        }
        return withDelegate(newDelegate);
    }

    @Override
    protected abstract SettableBeanProperty withDelegate(SettableBeanProperty d);

    public abstract SettableBeanProperty withMutator(BeanPropertyMutator mut);

    /*
    /********************************************************************** 
    /* Deserialization, regular
    /********************************************************************** 
     */

    @Override
    public abstract void deserializeAndSet(JsonParser jp, DeserializationContext ctxt,
            Object arg2) throws IOException;

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
        return delegate.setAndReturn(instance, value);
    }

    /*
    /********************************************************************** 
    /* Extended API
    /********************************************************************** 
     */

    public int getOptimizedIndex() {
        return _optimizedIndex;
    }

    /*
    /********************************************************************** 
    /* Error handling
    /********************************************************************** 
     */

    /**
     * Helper method called when an exception is throw from mutator, to figure
     * out what to do.
     *
     * @since 2.9
     */
    protected void _reportProblem(Object bean, Object value, Throwable e)
        throws IOException
    {
        if ((e instanceof IllegalAccessError)
                || (e instanceof SecurityException)) {
            synchronized (this) {
                // yes, double-locking, so not guaranteed; but all we want here is to reduce likelihood
                // of multiple logging of same underlying problem. Not guaranteed, just improved.
                if (!broken) {
                    broken = true;
                    String msg = String.format("Disabling Afterburner deserialization for %s (field #%d; mutator %s), due to access error (type %s, message=%s)%n",
                            bean.getClass(), _optimizedIndex, getClass().getName(),
                            e.getClass().getName(), e.getMessage());
                    Logger.getLogger(BeanPropertyMutator.class.getName()).log(Level.WARNING, msg, e);
                    // and for next time around, should be re-routed:
                    _propertyMutator = new DelegatingPropertyMutator(delegate);
                }
            }
            delegate.set(bean, value);
            return;
        }
        if (e instanceof IOException) {
            throw (IOException) e;
        }
        if (e instanceof Error) {
            throw (Error) e;
        }
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        }
        // how could this ever happen?
        throw new RuntimeException(e);
    }

    /*
    /********************************************************************** 
    /* Helper methods
    /********************************************************************** 
     */

    protected final boolean _deserializeBoolean(JsonParser p, DeserializationContext ctxt) throws IOException
    {
        JsonToken t = p.currentToken();
        if (t == JsonToken.VALUE_TRUE) return true;
        if (t == JsonToken.VALUE_FALSE) return false;
        if (t == JsonToken.VALUE_NULL) {
            if (ctxt.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)) {
                _failNullToPrimitiveCoercion(ctxt, "boolean");
            }
            return false;
        }

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
                _handleMissingEndArrayForSingle(p, ctxt);
            }            
            return parsed;            
        }
        // Otherwise, no can do:
        return (Boolean) ctxt.handleUnexpectedToken(Boolean.TYPE, p);
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
        JsonToken t = p.currentToken();
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
            if (ctxt.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)) {
                _failNullToPrimitiveCoercion(ctxt, "int");
            }
            return 0;
        }
        if (t == JsonToken.START_ARRAY && ctxt.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
            p.nextToken();
            final int parsed = _deserializeInt(p, ctxt);
            t = p.nextToken();
            if (t != JsonToken.END_ARRAY) {
                _handleMissingEndArrayForSingle(p, ctxt);
            }            
            return parsed;            
        }
        // Otherwise, no can do:
        return (Integer) ctxt.handleUnexpectedToken(Integer.TYPE, p);
    }

    protected final long _deserializeLong(JsonParser p, DeserializationContext ctxt)
        throws IOException
    {
        switch (p.currentTokenId()) {
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
            if (ctxt.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)) {
                _failNullToPrimitiveCoercion(ctxt, "long");
            }
            return 0L;
        case JsonTokenId.ID_START_ARRAY:
            if (ctxt.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
                p.nextToken();
                final long parsed = _deserializeLong(p, ctxt);
                JsonToken t = p.nextToken();
                if (t != JsonToken.END_ARRAY) {
                    _handleMissingEndArrayForSingle(p, ctxt);
                }            
                return parsed;
            }
            break;
        }
        return (Long) ctxt.handleUnexpectedToken(Long.TYPE, p);
    }

    protected final String _deserializeString(JsonParser p, DeserializationContext ctxt) throws IOException
    {
        switch (p.currentTokenId()) {
        case JsonTokenId.ID_STRING:
            return p.getText();
        case JsonTokenId.ID_NULL:
            return null;
        case JsonTokenId.ID_START_ARRAY:
            if (ctxt.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
                p.nextToken();
                final String parsed = _deserializeString(p, ctxt);
                if (p.nextToken() != JsonToken.END_ARRAY) {
                    _handleMissingEndArrayForSingle(p, ctxt);
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
        return (String) ctxt.handleUnexpectedToken(String.class, p);
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

    protected void _failNullToPrimitiveCoercion(DeserializationContext ctxt, String type) throws JsonMappingException
    {
        ctxt.reportInputMismatch(getType(),
                "Cannot map `null` into type %s (set DeserializationConfig.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES to 'false' to allow)",
                type);
    }

    protected void _failDoubleToIntCoercion(JsonParser p, DeserializationContext ctxt,
            String type) throws IOException
    {
        ctxt.reportInputMismatch(getType(),
                "Can not coerce a floating-point value (%s) into %s; enable `DeserializationFeature.ACCEPT_FLOAT_AS_INT` to allow",
                p.getValueAsString(), type);
    }

    protected boolean _hasTextualNull(String value) {
        return "null".equals(value);
    }

    /**
     * Helper method used to check whether given deserializer is the default
     * deserializer implementation: this is necessary to avoid overriding custom
     * deserializers.
     */
    protected boolean _isDefaultDeserializer(JsonDeserializer<?> deser) {
        return (deser == null)
                || (deser instanceof SuperSonicBeanDeserializer)
                || ClassUtil.isJacksonStdImpl(deser);
    }

    protected void _handleMissingEndArrayForSingle(JsonParser p, DeserializationContext ctxt)
        throws IOException
    {
        JavaType type = getType();
        ctxt.reportWrongTokenException(type, JsonToken.END_ARRAY, 
"Attempted to unwrap single value array for single %s value but there was more than a single value in the array",
getType());
        // 05-May-2016, tatu: Should recover somehow (maybe skip until END_ARRAY);
        //     but for now just fall through
    }
}
