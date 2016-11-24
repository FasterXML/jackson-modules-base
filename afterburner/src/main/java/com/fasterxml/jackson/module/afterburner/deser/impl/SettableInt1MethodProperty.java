package com.fasterxml.jackson.module.afterburner.deser.impl;

import java.io.IOException;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

import com.fasterxml.jackson.module.afterburner.deser.*;

public final class SettableInt1MethodProperty
    extends OptimizedSettableBeanProperty<SettableInt1MethodProperty>
{
    private static final long serialVersionUID = 1L;

    public SettableInt1MethodProperty(SettableBeanProperty src,
            BeanPropertyMutator mutator, int index)
    {
        super(src, mutator, index);
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableInt1MethodProperty(del, _propertyMutator, _optimizedIndex);
    }

    @Override
    public SettableBeanProperty withMutator(BeanPropertyMutator mut) {
        return new SettableInt1MethodProperty(delegate, mut, _optimizedIndex);
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
            _propertyMutator.intSetter1(bean, v);
        } catch (Throwable e) {
            _reportProblem(bean, v, e);
        }
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        // not optimal (due to boxing), but better than using reflection:
        int v = ((Number) value).intValue();
        try {
            _propertyMutator.intSetter1(bean, v);
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
