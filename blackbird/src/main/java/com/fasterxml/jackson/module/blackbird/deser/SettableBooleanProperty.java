package com.fasterxml.jackson.module.blackbird.deser;

import java.io.IOException;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

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
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt, Object bean) throws IOException {
        boolean b;
        JsonToken t = p.currentToken();
        if (t == JsonToken.VALUE_TRUE) {
            b = true;
        } else if (t == JsonToken.VALUE_FALSE) {
            b = false;
        } else {
            b = _deserializeBoolean(p, ctxt);
        }
        try {
            _optimizedSetter.accept(bean, b);
        } catch (Throwable e) {
            _reportProblem(bean, b, e);
        }
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        // not optimal (due to boxing), but better than using reflection:
        final boolean b = ((Boolean) value).booleanValue();
        try {
            _optimizedSetter.accept(bean, b);
        } catch (Throwable e) {
            _reportProblem(bean, b, e);
        }
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance) throws IOException
    {
        boolean b;
        JsonToken t = p.currentToken();
        if (t == JsonToken.VALUE_TRUE) {
            b = true;
        } else if (t == JsonToken.VALUE_FALSE) {
            b = false;
        } else {
            b = _deserializeBoolean(p, ctxt);
        }
        return setAndReturn(instance, b);
    }
}
