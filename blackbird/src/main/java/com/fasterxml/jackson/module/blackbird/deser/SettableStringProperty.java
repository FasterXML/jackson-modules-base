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
            BiFunction<Object, Object, Object> optimizedSetterAndReturn, Class<?> receiverType, boolean forBuilder)
    {
        super(src, receiverType, forBuilder);
        _optimizedSetter = optimizedSetter;
        _optimizedSetterAndReturn = optimizedSetterAndReturn;
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableStringProperty(del, _optimizedSetter, _optimizedSetterAndReturn, _receiverType, _forBuilder);
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
        _set(p, bean, p.getText());
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p, DeserializationContext ctxt, Object instance) throws IOException
    {
        if ((_optimizedSetterAndReturn != null) && _receiverType.isInstance(instance)
                && p.hasToken(JsonToken.VALUE_STRING)) {
            String text = p.getText();
            // some parsers give null here (e.g. for a `TextNode(null)`), which needs
            // the delegate's null handling
            if (text != null) {
                return _setAndReturn(p, instance, text);
            }
        }
        return delegate.deserializeSetAndReturn(p, ctxt, instance);
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        // as in `MethodProperty`
        if ((value == null) && _skipNulls) {
            return;
        }
        _set(null, bean, value);
    }

    private void _set(JsonParser p, Object bean, Object value) throws IOException {
        try {
            _optimizedSetter.accept(bean, (String) value);
        } catch (Throwable e) {
            _reportProblem(p, bean, value, e);
        }
    }

    @Override
    public Object setAndReturn(Object instance, Object value) throws IOException {
        // a value of another type, or an instance the generated setter can't take,
        // is left to the delegate, which reports it
        if ((_optimizedSetterAndReturn == null) || !_receiverType.isInstance(instance)
                || ((value != null) && !(value instanceof String))) {
            return delegate.setAndReturn(instance, value);
        }
        // as in `MethodProperty`, only here: `deserializeSetAndReturn` already skipped
        // the nulls it should
        if ((value == null) && _skipNulls) {
            return instance;
        }
        return _setAndReturn(null, instance, value);
    }

    private Object _setAndReturn(JsonParser p, Object instance, Object value) throws IOException {
        try {
            Object result = _optimizedSetterAndReturn.apply(instance, value);
            return (result == null) ? instance : result;
        } catch (Throwable e) {
            return _reportProblemAndReturn(p, e);
        }
    }
}
