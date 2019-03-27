package com.fasterxml.jackson.module.blackbird.deser;

import java.io.IOException;
import java.util.function.ObjIntConsumer;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

final class SettableIntProperty
    extends OptimizedSettableBeanProperty<SettableIntProperty>
{
    private static final long serialVersionUID = 1L;
    private ObjIntConsumer<Object> _optimizedSetter;

    public SettableIntProperty(SettableBeanProperty src, ObjIntConsumer<Object> optimizedSetter)
    {
        super(src);
        _optimizedSetter = optimizedSetter;
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableIntProperty(del, _optimizedSetter);
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
        int v = p.hasToken(JsonToken.VALUE_NUMBER_INT) ? p.getIntValue() : _deserializeInt(p, ctxt);
        try {
            _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            _reportProblem(bean, v, e);
        }
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        // not optimal (due to boxing), but better than using reflection:
        int v = ((Number) value).intValue();
        try {
            _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            _reportProblem(bean, v, e);
        }
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance)
        throws IOException
    {
        int v = p.hasToken(JsonToken.VALUE_NUMBER_INT) ? p.getIntValue() : _deserializeInt(p, ctxt);
        return setAndReturn(instance, v);
    }
}
