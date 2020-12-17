package com.fasterxml.jackson.module.blackbird.deser;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.core.io.NumberInput;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.deser.*;
import com.fasterxml.jackson.databind.deser.impl.NullsConstantProvider;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.util.ClassUtil;

/**
 * Base class for concrete type-specific {@link SettableBeanProperty}
 * implementations.
 */
abstract class OptimizedSettableBeanProperty<T extends OptimizedSettableBeanProperty<T>>
    extends SettableBeanProperty.Delegating
{
    private static final long serialVersionUID = 1L;

    /**
     * @since 2.9
     */
    final protected boolean _skipNulls;

    /**
     * Marker that we set if mutator turns out to be broken in a systematic
     * way that we can handle by redirecting it back to standard one.
     */
    private volatile boolean broken = false;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    protected OptimizedSettableBeanProperty(SettableBeanProperty src)
    {
        super(src);
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
                    String msg = String.format("Disabling Blackbird deserialization for %s (field %s; mutator %s), due to access error (type %s, message=%s)%n",
                            bean.getClass(), _propName, getClass().getName(),
                            e.getClass().getName(), e.getMessage());
                    Logger.getLogger(getClass().getName()).log(Level.WARNING, msg, e);
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
            _verifyScalarCoercion(ctxt, p, "boolean");
            // 11-Jan-2012, tatus: May be outside of int...
            if (p.getNumberType() == NumberType.INT) {
                return (p.getIntValue() != 0);
            }
            return _deserializeBooleanFromOther(p, ctxt);
        }
        // And finally, let's allow Strings to be converted too
        if (t == JsonToken.VALUE_STRING) {
            _verifyScalarCoercion(ctxt, p, "boolean");
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

    // 16-Dec-2020, tatu: Copied from "StdDeserializer._parseIntPrimitive()" verbatim:
    //
    // @since 2.12.1
    protected final int _deserializeInt(JsonParser p, DeserializationContext ctxt)
        throws IOException
    {
        String text;
        switch (p.currentTokenId()) {
        case JsonTokenId.ID_STRING:
            text = p.getText();
            break;
        case JsonTokenId.ID_NUMBER_FLOAT:
            final CoercionAction act = _checkFloatToIntCoercion(p, ctxt, Integer.TYPE);
            if (act == CoercionAction.AsNull) {
                return 0;
            }
            if (act == CoercionAction.AsEmpty) {
                return 0;
            }
            return p.getValueAsInt();
        case JsonTokenId.ID_NUMBER_INT:
            return p.getIntValue();
        case JsonTokenId.ID_NULL:
            _verifyNullForPrimitive(ctxt);
            return 0;
        // 16-Dec-2020, tatu: not sure if this will work (no deserializer to pass),
         //    but we'll do our best
        case JsonTokenId.ID_START_OBJECT:
            text = ctxt.extractScalarFromObject(p, /* deserializer */ null, Integer.TYPE);
            break;
        case JsonTokenId.ID_START_ARRAY:
            if (ctxt.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
                p.nextToken();
                final int parsed = _deserializeInt(p, ctxt);
                _verifyEndArrayForSingle(p, ctxt);
                return parsed;
            }
            // fall through to fail
        default:
            return ((Number) ctxt.handleUnexpectedToken(getType(), p)).intValue();
        }

        final CoercionAction act = _checkFromStringCoercion(ctxt, text,
                LogicalType.Integer, Integer.TYPE);
        if (act == CoercionAction.AsNull) {
            return 0; // no need to check as does not come from `null`, explicit coercion
        }
        if (act == CoercionAction.AsEmpty) {
            return 0;
        }
        text = text.trim();
        if (_hasTextualNull(text)) {
            _verifyNullForPrimitiveCoercion(ctxt, text);
            return 0;
        }
        return _parseIntPrimitive(ctxt, text);
    }

    private final int _parseIntPrimitive(DeserializationContext ctxt, String text) throws IOException
    {
        try {
            if (text.length() > 9) {
                long l = Long.parseLong(text);
                if (_intOverflow(l)) {
                    Number v = (Number) ctxt.handleWeirdStringValue(Integer.TYPE, text,
                        "Overflow: numeric value (%s) out of range of int (%d -%d)",
                        text, Integer.MIN_VALUE, Integer.MAX_VALUE);
                    return _nonNullNumber(v).intValue();
                }
                return (int) l;
            }
            return NumberInput.parseInt(text);
        } catch (IllegalArgumentException iae) {
            Number v = (Number) ctxt.handleWeirdStringValue(Integer.TYPE, text,
                    "not a valid `int` value");
            return _nonNullNumber(v).intValue();
        }
    }

    // 16-Dec-2020, tatu: Copied from "StdDeserializer._parseLongPrimitive()" verbatim:
    //
    // @since 2.12.1
    protected final long _deserializeLong(JsonParser p, DeserializationContext ctxt)
        throws IOException
    {
        String text;
        switch (p.currentTokenId()) {
        case JsonTokenId.ID_STRING:
            text = p.getText();
            break;
        case JsonTokenId.ID_NUMBER_FLOAT:
            final CoercionAction act = _checkFloatToIntCoercion(p, ctxt, Long.TYPE);
            if (act == CoercionAction.AsNull) {
                return 0;
            }
            if (act == CoercionAction.AsEmpty) {
                return 0;
            }
            return p.getValueAsInt();
        case JsonTokenId.ID_NUMBER_INT:
            return p.getIntValue();
        case JsonTokenId.ID_NULL:
            _verifyNullForPrimitive(ctxt);
            return 0;
        // 16-Dec-2020, tatu: not sure if this will work (no deserializer to pass),
         //    but we'll do our best
        case JsonTokenId.ID_START_OBJECT:
            text = ctxt.extractScalarFromObject(p, /* deserializer */ null, Long.TYPE);
            break;
        case JsonTokenId.ID_START_ARRAY:
            if (ctxt.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
                p.nextToken();
                final long parsed = _deserializeLong(p, ctxt);
                _verifyEndArrayForSingle(p, ctxt);
                return parsed;
            }
            // fall through to fail
        default:
            return ((Number) ctxt.handleUnexpectedToken(getType(), p)).intValue();
        }

        final CoercionAction act = _checkFromStringCoercion(ctxt, text,
                LogicalType.Integer, Long.TYPE);
        if (act == CoercionAction.AsNull) {
            return 0; // no need to check as does not come from `null`, explicit coercion
        }
        if (act == CoercionAction.AsEmpty) {
            return 0;
        }
        text = text.trim();
        if (_hasTextualNull(text)) {
            _verifyNullForPrimitiveCoercion(ctxt, text);
            return 0;
        }
        return _parseLongPrimitive(ctxt, text);
    }

    private final long _parseLongPrimitive(DeserializationContext ctxt, String text) throws IOException
    {
        try {
            return NumberInput.parseLong(text);
        } catch (IllegalArgumentException iae) { }
        {
            Number v = (Number) ctxt.handleWeirdStringValue(Long.TYPE, text,
                    "not a valid `long` value");
            return _nonNullNumber(v).longValue();
        }
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

    private void _verifyScalarCoercion(DeserializationContext ctxt, JsonParser parser, String type) throws IOException {
        MapperFeature feat = MapperFeature.ALLOW_COERCION_OF_SCALARS;
        if (!ctxt.isEnabled(feat)) {
            ctxt.reportInputMismatch(getType(),
                    "Cannot coerce JSON %s value (%s) into %s (enable `%s.%s` to allow)",
                    parser.currentToken().name(),
                    parser.readValueAsTree(),
                    type,
                    feat.getClass().getSimpleName(),
                    feat.name());
        }
    }

    private boolean _hasTextualNull(String value) {
        return "null".equals(value);
    }

    private final boolean _intOverflow(long value) {
        return (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE);
    }

    private Number _nonNullNumber(Number n) {
        if (n == null) {
            n = Integer.valueOf(0);
        }
        return n;
    }

    protected final static boolean _isBlank(String text)
    {
        final int len = text.length();
        for (int i = 0; i < len; ++i) {
            if (text.charAt(i) > 0x0020) {
                return false;
            }
        }
        return true;
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

    private void _verifyEndArrayForSingle(JsonParser p, DeserializationContext ctxt) throws IOException
    {
        JsonToken t = p.nextToken();
        if (t != JsonToken.END_ARRAY) {
            _handleMissingEndArrayForSingle(p, ctxt);
        }
    }

    private void _handleMissingEndArrayForSingle(JsonParser p, DeserializationContext ctxt)
        throws IOException
    {
        JavaType type = getType();
        ctxt.reportWrongTokenException(type, JsonToken.END_ARRAY, 
"Attempted to unwrap single value array for single %s value but there was more than a single value in the array",
getType());
        // 05-May-2016, tatu: Should recover somehow (maybe skip until END_ARRAY);
        //     but for now just fall through
    }

    /*
    /********************************************************************** 
    /* New methods in 2.12.1 to align CoercionConfig handling with databind
    /* (copied from "StdDeserializer")
    /********************************************************************** 
     */

    private CoercionAction _checkFloatToIntCoercion(JsonParser p, DeserializationContext ctxt,
            Class<?> rawTargetType)
        throws IOException
    {
        final CoercionAction act = ctxt.findCoercionAction(LogicalType.Integer,
                rawTargetType, CoercionInputShape.Float);
        if (act == CoercionAction.Fail) {
            return _checkCoercionFail(ctxt, act, rawTargetType, p.getNumberValue(),
                    "Floating-point value ("+p.getText()+")");
        }
        return act;
    }

    private CoercionAction _checkFromStringCoercion(DeserializationContext ctxt, String value,
            LogicalType logicalType, Class<?> rawTargetType)
        throws IOException
    {
        final CoercionAction act;

        if (value.isEmpty()) {
            act = ctxt.findCoercionAction(logicalType, rawTargetType,
                    CoercionInputShape.EmptyString);
            return _checkCoercionFail(ctxt, act, rawTargetType, value,
                    "empty String (\"\")");
        } else if (_isBlank(value)) {
            act = ctxt.findCoercionFromBlankString(logicalType, rawTargetType, CoercionAction.Fail);
            return _checkCoercionFail(ctxt, act, rawTargetType, value,
                    "blank String (all whitespace)");
        } else {
            act = ctxt.findCoercionAction(logicalType, rawTargetType, CoercionInputShape.String);
            if (act == CoercionAction.Fail) {
                // since it MIGHT (but might not), create desc here, do not use helper
                ctxt.reportInputMismatch(this,
"Cannot coerce String value (\"%s\") to %s (but might if coercion using `CoercionConfig` was enabled)",
value, _coercedTypeDesc());
            }
        }
        return act;
    }

    private CoercionAction _checkCoercionFail(DeserializationContext ctxt,
            CoercionAction act, Class<?> targetType, Object inputValue,
            String inputDesc)
        throws IOException
    {
        if (act == CoercionAction.Fail) {
            // 16-Dec-2020, tatu: Let's hope `null` for deserializer is ok...
            ctxt.reportBadCoercion(null, targetType, inputValue,
"Cannot coerce %s to %s (but could if coercion was enabled using `CoercionConfig`)",
inputDesc, _coercedTypeDesc());
        }
        return act;
    }

    private void _verifyNullForPrimitive(DeserializationContext ctxt)
            throws JsonMappingException
    {
        if (ctxt.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)) {
            ctxt.reportInputMismatch(this,
"Cannot coerce `null` to %s (disable `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES` to allow)",
                    _coercedTypeDesc());
        }
    }

    private final void _verifyNullForPrimitiveCoercion(DeserializationContext ctxt,
            String str) throws JsonMappingException
    {
        if (ctxt.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)) {
            String strDesc = str.isEmpty() ? "empty String (\"\")" : String.format("String \"%s\"", str);
            ctxt.reportInputMismatch(this,
"Cannot coerce %s to %s (disable `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES` to allow)",
                    strDesc, _coercedTypeDesc());
        }
    }
    
    // Simplified as we only ever get simple scalar types
    private String _coercedTypeDesc() {
        return ClassUtil.getTypeDescription(getType()) +" value";
    }
}
