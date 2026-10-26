package com.fasterxml.jackson.module.blackbird.deser;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

final class SettableObjectProperty
    extends OptimizedSettableBeanProperty<SettableObjectProperty>
{
    private static final long serialVersionUID = 1L;
    private BiConsumer<Object, Object> _optimizedSetter;
    private BiFunction<Object, Object, Object> _optimizedSetterAndReturn;

    public SettableObjectProperty(SettableBeanProperty src, BiConsumer<Object, Object> optimizedSetter,
            BiFunction<Object, Object, Object> optimizedSetterAndReturn)
    {
        super(src);
        this._optimizedSetter = optimizedSetter;
        this._optimizedSetterAndReturn = optimizedSetterAndReturn;
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableObjectProperty(del, _optimizedSetter, _optimizedSetterAndReturn);
    }

    /*
    /**********************************************************************
    /* Deserialization
    /**********************************************************************
     */

    @Override
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt,
            Object bean) throws IOException
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
        set(bean, value);
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance) throws IOException
    {
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
        return setAndReturn(instance, value);
    }

    @Override
    public void set(Object bean, Object v) throws IOException {
        try {
            _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            _reportProblem(bean, v, e);
        }
    }

    @Override
    public Object setAndReturn(Object instance, Object value) throws IOException {
        if (_optimizedSetterAndReturn == null) {
            return delegate.setAndReturn(instance, value);
        }
        if ((value == null) && _skipNulls) {
            return instance;
        }
        try {
            Object result = _optimizedSetterAndReturn.apply(instance, value);
            return (result == null) ? instance : result;
        } catch (Throwable e) {
            return _reportProblemAndReturn(instance, value, e);
        }
    }
}
