package tools.jackson.module.blackbird.deser;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.deser.SettableBeanProperty;

final class SettableObjectProperty
    extends OptimizedSettableBeanProperty<SettableObjectProperty>
{
    private static final long serialVersionUID = 1L;
    private BiConsumer<Object, Object> _optimizedSetter;
    private BiFunction<Object, Object, Object> _optimizedSetterAndReturn;
    private final Class<?> _setterValueType;

    public SettableObjectProperty(SettableBeanProperty src, BiConsumer<Object, Object> optimizedSetter,
            BiFunction<Object, Object, Object> optimizedSetterAndReturn, Class<?> receiverType,
            Class<?> setterValueType, boolean forBuilder)
    {
        super(src, receiverType, forBuilder);
        this._optimizedSetter = optimizedSetter;
        this._optimizedSetterAndReturn = optimizedSetterAndReturn;
        _setterValueType = setterValueType;
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableObjectProperty(del, _optimizedSetter, _optimizedSetterAndReturn, _receiverType,
                _setterValueType, _forBuilder);
    }

    /*
    /**********************************************************************
    /* Deserialization
    /**********************************************************************
     */

    @Override
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt,
            Object bean) throws JacksonException
    {
        Object value;
        if (p.hasToken(JsonToken.VALUE_NULL)) {
            if (_skipNulls) {
                return;
            }
            value = _nullProvider.getNullValue(ctxt);
        } else if (_valueTypeDeserializer == null) {
            value = _valueDeserializer.deserialize(p, ctxt);
            // 04-May-2018, tatu: [databind#2023] Coercion from String (mostly) can give null
            if (value == null) {
                if (_skipNulls) {
                    return;
                }
                value = _nullProvider.getNullValue(ctxt);
            }
        } else {
            value = _valueDeserializer.deserializeWithType(p, ctxt, _valueTypeDeserializer);
        }
        _set(ctxt, p, bean, value);
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance) throws JacksonException
    {
        if ((_optimizedSetterAndReturn == null) || !_receiverType.isInstance(instance)) {
            return delegate.deserializeSetAndReturn(p, ctxt, instance);
        }
        Object value;
        if (p.hasToken(JsonToken.VALUE_NULL)) {
            if (_skipNulls) {
                return instance;
            }
            value = _nullProvider.getNullValue(ctxt);
        } else if (_valueTypeDeserializer == null) {
            value = _valueDeserializer.deserialize(p, ctxt);
            // 04-May-2018, tatu: [databind#2023] Coercion from String (mostly) can give null
            if (value == null) {
                if (_skipNulls) {
                    return instance;
                }
                value = _nullProvider.getNullValue(ctxt);
            }
        } else {
            value = _valueDeserializer.deserializeWithType(p, ctxt, _valueTypeDeserializer);
        }
        // a deserializer can give a value of another type (e.g. from a creator declared
        // to return `Object`), which the generated setter can't take; the delegate
        // reports it as it would
        if ((value != null) && !_setterValueType.isInstance(value)) {
            return delegate.setAndReturn(ctxt, instance, value);
        }
        return _setAndReturn(p, instance, value);
    }

    @Override
    public void set(DeserializationContext ctxt, Object bean, Object v) throws JacksonException {
        // as in `MethodProperty`, only here: `deserializeAndSet` already skipped
        // the nulls it should
        if ((v == null) && _skipNulls) {
            return;
        }
        _set(ctxt, ctxt.getParser(), bean, v);
    }

    private void _set(DeserializationContext ctxt, JsonParser p, Object bean, Object v) throws JacksonException {
        // a value of another type (see `deserializeSetAndReturn`), or a builder the
        // generated setter can't take, is left to the delegate, which reports it
        if (((v != null) && !_setterValueType.isInstance(v))
                || (_forBuilder && !_receiverType.isInstance(bean))) {
            delegate.set(ctxt, bean, v);
            return;
        }
        try {
            _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            _reportProblem(ctxt, p, bean, v, e);
        }
    }

    @Override
    public Object setAndReturn(DeserializationContext ctxt, Object instance, Object value) throws JacksonException {
        // a value of another type, or an instance the generated setter can't take,
        // is left to the delegate, which reports it
        if ((_optimizedSetterAndReturn == null) || !_receiverType.isInstance(instance)
                || ((value != null) && !_setterValueType.isInstance(value))) {
            return delegate.setAndReturn(ctxt, instance, value);
        }
        // as in `MethodProperty`, only here: `deserializeSetAndReturn` already skipped
        // the nulls it should
        if ((value == null) && _skipNulls) {
            return instance;
        }
        return _setAndReturn(ctxt.getParser(), instance, value);
    }

    private Object _setAndReturn(JsonParser p, Object instance, Object value) throws JacksonException {
        try {
            Object result = _optimizedSetterAndReturn.apply(instance, value);
            return (result == null) ? instance : result;
        } catch (Throwable e) {
            return _reportProblemAndReturn(p, value, e);
        }
    }
}
