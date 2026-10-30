package com.fasterxml.jackson.module.blackbird.deser;

import java.io.IOException;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.module.blackbird.deser.BBDeserializerModifier.ObjBooleanBiFunction;

final class SettableBooleanProperty
    extends OptimizedSettableBeanProperty<SettableBooleanProperty>
{
    private static final long serialVersionUID = 1L;
    private ObjBooleanConsumer _optimizedSetter;
    private ObjBooleanBiFunction _optimizedSetterAndReturn;

    public SettableBooleanProperty(SettableBeanProperty src, ObjBooleanConsumer optimizedSetter,
            ObjBooleanBiFunction optimizedSetterAndReturn, Class<?> receiverType, boolean forBuilder)
    {
        super(src, receiverType, forBuilder);
        _optimizedSetter = optimizedSetter;
        _optimizedSetterAndReturn = optimizedSetterAndReturn;
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableBooleanProperty(del, _optimizedSetter, _optimizedSetterAndReturn, _receiverType, _forBuilder);
    }

    /*
    /**********************************************************************
    /* Deserialization
    /**********************************************************************
     */

    @Override
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt, Object bean) throws IOException {
        boolean b;
        JsonToken t = p.currentToken();
        if (t == JsonToken.VALUE_TRUE) {
            b = true;
        } else if (t == JsonToken.VALUE_FALSE) {
            b = false;
        } else {
            delegate.deserializeAndSet(p, ctxt, bean);
            return;
        }
        try {
            _optimizedSetter.accept(bean, b);
        } catch (Throwable e) {
            _reportProblem(p, bean, b, e);
        }
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance) throws IOException
    {
        if ((_optimizedSetterAndReturn != null) && _receiverType.isInstance(instance)) {
            JsonToken t = p.currentToken();
            if (t == JsonToken.VALUE_TRUE) {
                return _setAndReturn(p, instance, true);
            }
            if (t == JsonToken.VALUE_FALSE) {
                return _setAndReturn(p, instance, false);
            }
        }
        return delegate.deserializeSetAndReturn(p, ctxt, instance);
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        // not optimal (due to boxing), but better than using reflection:
        final boolean b = ((Boolean) value).booleanValue();
        try {
            _optimizedSetter.accept(bean, b);
        } catch (Throwable e) {
            _reportProblem(null, bean, b, e);
        }
    }

    @Override
    public Object setAndReturn(Object instance, Object value) throws IOException {
        // a void setter, anything else (null, or another type that reflection may widen
        // or reject), or an instance the generated setter can't take, is left to the delegate
        if ((_optimizedSetterAndReturn == null) || !(value instanceof Boolean)
                || !_receiverType.isInstance(instance)) {
            return delegate.setAndReturn(instance, value);
        }
        // not optimal (due to boxing), but better than using reflection:
        return _setAndReturn(null, instance, ((Boolean) value).booleanValue());
    }

    private Object _setAndReturn(JsonParser p, Object instance, boolean b) throws IOException {
        try {
            Object result = _optimizedSetterAndReturn.apply(instance, b);
            return (result == null) ? instance : result;
        } catch (Throwable e) {
            return _reportProblemAndReturn(p, b, e);
        }
    }
}
