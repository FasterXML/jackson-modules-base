package com.fasterxml.jackson.module.afterburner.deser.impl;

import java.io.IOException;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

import com.fasterxml.jackson.module.afterburner.deser.*;

public final class SettableString1MethodProperty
    extends OptimizedSettableBeanProperty<SettableString1MethodProperty>
{
    private static final long serialVersionUID = 1L;

    public SettableString1MethodProperty(SettableBeanProperty src,
            BeanPropertyMutator mutator, int index)
    {
        super(src, mutator, index);
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableString1MethodProperty(del, _propertyMutator, _optimizedIndex);
    }

    @Override
    public SettableBeanProperty withMutator(BeanPropertyMutator mut) {
        return new SettableString1MethodProperty(delegate, mut, _optimizedIndex);
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
        String text = p.getValueAsString();
        if (text == null) {
            text = _deserializeString(p, ctxt);
        }
        try {
            _propertyMutator.stringSetter1(bean, text);
        } catch (Throwable e) {
            _reportProblem(bean, text, e);
        }
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        final String text = (String) value;
        try {
            _propertyMutator.stringSetter1(bean, text);
        } catch (Throwable e) {
            _reportProblem(bean, text, e);
        }
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p, DeserializationContext ctxt, Object instance) throws IOException
    {
        String text = p.getValueAsString();
        if (text == null) {
            text = _deserializeString(p, ctxt);
        }
        return setAndReturn(instance, text);
    }
}
