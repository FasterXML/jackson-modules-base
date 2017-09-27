package com.fasterxml.jackson.module.afterburner.deser;

import java.io.IOException;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

public final class SettableBooleanFieldProperty
    extends OptimizedSettableBeanProperty<SettableBooleanFieldProperty>
{
    private static final long serialVersionUID = 1L;

    public SettableBooleanFieldProperty(SettableBeanProperty src,
            BeanPropertyMutator mutator, int index)
    {
        super(src, mutator, index);
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableBooleanFieldProperty(del, _propertyMutator, _optimizedIndex);
    }

    @Override
    public SettableBeanProperty withMutator(BeanPropertyMutator mut) {
        return new SettableBooleanFieldProperty(delegate, mut, _optimizedIndex);
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
            _propertyMutator.booleanField(bean, _optimizedIndex, b);
        } catch (Throwable e) {
            _reportProblem(bean, b, e);
        }
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        // not optimal (due to boxing), but better than using reflection:
        final boolean b = ((Boolean) value).booleanValue();
        try {
            _propertyMutator.booleanField(bean, _optimizedIndex, b);
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
