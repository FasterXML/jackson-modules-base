package com.fasterxml.jackson.module.blackbird.deser;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

final class SettableStringProperty
    extends OptimizedSettableBeanProperty<SettableStringProperty>
{
    private static final long serialVersionUID = 1L;
    private final BiConsumer<Object, String> _optimizedSetter;
    private final BiFunction<Object, Object, Object> _optimizedSetterAndReturn;

    public SettableStringProperty(SettableBeanProperty src, BiConsumer<Object, String> optimizedSetter,
            BiFunction<Object, Object, Object> optimizedSetterAndReturn)
    {
        super(src);
        _optimizedSetter = optimizedSetter;
        _optimizedSetterAndReturn = optimizedSetterAndReturn;
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableStringProperty(del, _optimizedSetter, _optimizedSetterAndReturn);
    }

    /*
    /**********************************************************************
    /* Deserialization
    /**********************************************************************
     */

    // Copied from StdDeserializer.StringDeserializer:
    @Override
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt, Object bean) throws IOException
    {
        if (!p.hasToken(JsonToken.VALUE_STRING)) {
            delegate.deserializeAndSet(p, ctxt, bean);
            return;
        }
        set(bean, p.getText());
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p, DeserializationContext ctxt, Object instance) throws IOException
    {
        if ((_optimizedSetterAndReturn != null) && p.hasToken(JsonToken.VALUE_STRING)) {
            return _setAndReturn(p, instance, p.getText());
        }
        return delegate.deserializeSetAndReturn(p, ctxt, instance);
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        try {
            _optimizedSetter.accept(bean, (String) value);
        } catch (Throwable e) {
            _reportProblem(bean, value, e);
        }
    }

    @Override
    public Object setAndReturn(Object instance, Object value) throws IOException {
        // a value of another type is left to the delegate, which reports it
        if ((_optimizedSetterAndReturn == null) || ((value != null) && !(value instanceof String))) {
            return delegate.setAndReturn(instance, value);
        }
        return _setAndReturn(null, instance, value);
    }

    private Object _setAndReturn(JsonParser p, Object instance, Object value) throws IOException {
        if ((value == null) && _skipNulls) {
            return instance;
        }
        try {
            Object result = _optimizedSetterAndReturn.apply(instance, value);
            return (result == null) ? instance : result;
        } catch (Throwable e) {
            return _reportProblemAndReturn(p, _optimizedSetterAndReturn, instance, value, e);
        }
    }
}
