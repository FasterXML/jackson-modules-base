package com.fasterxml.jackson.module.afterburner.deser;

import java.io.IOException;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

public final class SettableLongMethodProperty
    extends OptimizedSettableBeanProperty<SettableLongMethodProperty>
{
    private static final long serialVersionUID = 1L;

    public SettableLongMethodProperty(SettableBeanProperty src,
            BeanPropertyMutator mutator, int index)
    {
        super(src, mutator, index);
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableLongMethodProperty(del, _propertyMutator, _optimizedIndex);
    }

    @Override
    public SettableBeanProperty withMutator(BeanPropertyMutator mut) {
        return new SettableLongMethodProperty(delegate, mut, _optimizedIndex);
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
            _propertyMutator.longSetter(bean, _optimizedIndex, v);
        } catch (Throwable e) {
            _reportProblem(bean, v, e);
        }
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        // not optimal (due to boxing), but better than using reflection:
        final long v = ((Number) value).longValue();
        try {
            _propertyMutator.longSetter(bean, _optimizedIndex, v);
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
