package com.fasterxml.jackson.module.blackbird.deser;

import java.io.IOException;
import java.util.function.ObjLongConsumer;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.module.blackbird.deser.BBDeserializerModifier.ObjLongBiFunction;

final class SettableLongProperty
    extends OptimizedSettableBeanProperty<SettableLongProperty>
{
    private static final long serialVersionUID = 1L;
    private ObjLongConsumer<Object> _optimizedSetter;
    private ObjLongBiFunction _optimizedSetterAndReturn;

    public SettableLongProperty(SettableBeanProperty src, ObjLongConsumer<Object> optimizedSetter,
            ObjLongBiFunction optimizedSetterAndReturn)
    {
        super(src);
        _optimizedSetter = optimizedSetter;
        _optimizedSetterAndReturn = optimizedSetterAndReturn;
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableLongProperty(del, _optimizedSetter, _optimizedSetterAndReturn);
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
        final long v = p.getLongValue();
        try {
           _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            _reportProblem(bean, v, e);
        }
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance) throws IOException
    {
        if (p.isExpectedNumberIntToken()) {
            return _setAndReturn(instance, p.getLongValue());
        }
        return delegate.deserializeSetAndReturn(p, ctxt, instance);
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        // not optimal (due to boxing), but better than using reflection:
        final long v = ((Number) value).longValue();
        try {
            _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            _reportProblem(bean, v, e);
        }
    }

    @Override
    public Object setAndReturn(Object instance, Object value) throws IOException {
        if (value == null) {
            return delegate.setAndReturn(instance, value);
        }
        // not optimal (due to boxing), but better than using reflection:
        return _setAndReturn(instance, ((Number) value).longValue());
    }

    private Object _setAndReturn(Object instance, long v) throws IOException {
        if (_optimizedSetterAndReturn == null) {
            return delegate.setAndReturn(instance, v);
        }
        try {
            Object result = _optimizedSetterAndReturn.apply(instance, v);
            return (result == null) ? instance : result;
        } catch (Throwable e) {
            return _reportProblem(instance, v, e);
        }
    }
}
