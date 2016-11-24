package com.fasterxml.jackson.module.afterburner.deser.impl;

import java.io.IOException;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

import com.fasterxml.jackson.module.afterburner.deser.*;

public final class SettableLong1MethodProperty
    extends OptimizedSettableBeanProperty<SettableLong1MethodProperty>
{
    private static final long serialVersionUID = 1L;

    public SettableLong1MethodProperty(SettableBeanProperty src,
            BeanPropertyMutator mutator, int index)
    {
        super(src, mutator, index);
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableLong1MethodProperty(del, _propertyMutator, _optimizedIndex);
    }

    @Override
    public SettableBeanProperty withMutator(BeanPropertyMutator mut) {
        return new SettableLong1MethodProperty(delegate, mut, _optimizedIndex);
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
            _propertyMutator.longSetter1(bean, v);
        } catch (Throwable e) {
            _reportProblem(bean, v, e);
        }
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        // not optimal (due to boxing), but better than using reflection:
        final long v = ((Number) value).longValue();
        try {
            _propertyMutator.longSetter1(bean, v);
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
