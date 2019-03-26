package com.fasterxml.jackson.module.blackbird.deser;

import java.io.IOException;
import java.util.function.ObjLongConsumer;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

final class SettableLongProperty
    extends OptimizedSettableBeanProperty<SettableLongProperty>
{
    private static final long serialVersionUID = 1L;
    private ObjLongConsumer<Object> _optimizedSetter;

    public SettableLongProperty(SettableBeanProperty src, ObjLongConsumer<Object> optimizedSetter)
    {
        super(src);
        _optimizedSetter = optimizedSetter;
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableLongProperty(del, _optimizedSetter);
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
        long v = p.hasToken(JsonToken.VALUE_NUMBER_INT) ? p.getLongValue() : _deserializeLong(p, ctxt);
        try {
           _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            _reportProblem(bean, v, e);
        }
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
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance) throws IOException
    {
        long l = p.hasToken(JsonToken.VALUE_NUMBER_INT) ? p.getLongValue() : _deserializeLong(p, ctxt);
        return setAndReturn(instance, l);
    }
}
