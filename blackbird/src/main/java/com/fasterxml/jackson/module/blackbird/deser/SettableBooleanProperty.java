package com.fasterxml.jackson.module.blackbird.deser;

import tools.jackson.core.*;

import tools.jackson.databind.*;
import tools.jackson.databind.deser.SettableBeanProperty;

final class SettableBooleanProperty
    extends OptimizedSettableBeanProperty<SettableBooleanProperty>
{
    private static final long serialVersionUID = 1L;
    private ObjBooleanConsumer _optimizedSetter;

    public SettableBooleanProperty(SettableBeanProperty src, ObjBooleanConsumer optimizedSetter)
    {
        super(src);
        _optimizedSetter = optimizedSetter;
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableBooleanProperty(del, _optimizedSetter);
    }

    /*
    /**********************************************************************
    /* Deserialization
    /**********************************************************************
     */

    @Override
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt, Object bean)
        throws JacksonException
    {
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
            _reportProblem(bean, b, e);
        }
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance)
        throws JacksonException
    {
        JsonToken t = p.currentToken();
        if (t == JsonToken.VALUE_TRUE) {
            return setAndReturn(instance, Boolean.TRUE);
        }
        if (t == JsonToken.VALUE_FALSE) {
            return setAndReturn(instance, Boolean.FALSE);
        }
        return delegate.deserializeSetAndReturn(p, ctxt, instance);
    }

    @Override
    public void set(Object bean, Object value)
    {
        // not optimal (due to boxing), but better than using reflection:
        final boolean b = ((Boolean) value).booleanValue();
        try {
            _optimizedSetter.accept(bean, b);
        } catch (Throwable e) {
            _reportProblem(bean, b, e);
        }
    }
}
