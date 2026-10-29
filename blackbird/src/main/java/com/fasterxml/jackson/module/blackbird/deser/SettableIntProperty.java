package com.fasterxml.jackson.module.blackbird.deser;

import java.io.IOException;
import java.util.function.ObjIntConsumer;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.module.blackbird.deser.BBDeserializerModifier.ObjIntBiFunction;

final class SettableIntProperty
    extends OptimizedSettableBeanProperty<SettableIntProperty>
{
    private static final long serialVersionUID = 1L;
    private ObjIntConsumer<Object> _optimizedSetter;
    private ObjIntBiFunction _optimizedSetterAndReturn;

    public SettableIntProperty(SettableBeanProperty src, ObjIntConsumer<Object> optimizedSetter,
            ObjIntBiFunction optimizedSetterAndReturn)
    {
        super(src);
        _optimizedSetter = optimizedSetter;
        _optimizedSetterAndReturn = optimizedSetterAndReturn;
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableIntProperty(del, _optimizedSetter, _optimizedSetterAndReturn);
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
        if (!p.isExpectedNumberIntToken()) {
            delegate.deserializeAndSet(p, ctxt, bean);
            return;
        }
        final int v = p.getIntValue();
        try {
            _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            _reportProblem(_optimizedSetter, _optimizedSetterAndReturn, bean, v, e);
        }
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance)
        throws IOException
    {
        if (p.isExpectedNumberIntToken()) {
            return _setAndReturn(instance, p.getIntValue());
        }
        return delegate.deserializeSetAndReturn(p, ctxt, instance);
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        // not optimal (due to boxing), but better than using reflection:
        int v = ((Number) value).intValue();
        try {
            _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            _reportProblem(_optimizedSetter, _optimizedSetterAndReturn, bean, v, e);
        }
    }

    @Override
    public Object setAndReturn(Object instance, Object value) throws IOException {
        if (value == null) {
            return delegate.setAndReturn(instance, value);
        }
        // not optimal (due to boxing), but better than using reflection:
        return _setAndReturn(instance, ((Number) value).intValue());
    }

    private Object _setAndReturn(Object instance, int v) throws IOException {
        if (_optimizedSetterAndReturn == null) {
            return delegate.setAndReturn(instance, v);
        }
        try {
            Object result = _optimizedSetterAndReturn.apply(instance, v);
            return (result == null) ? instance : result;
        } catch (Throwable e) {
            return _reportProblemAndReturn(_optimizedSetterAndReturn, instance, v, e);
        }
    }
}
