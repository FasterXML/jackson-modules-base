package com.fasterxml.jackson.module.afterburner.deser;

import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.deser.SettableBeanProperty;

public final class SettableIntFieldProperty
    extends OptimizedSettableBeanProperty<SettableIntFieldProperty>
{
    private static final long serialVersionUID = 1L;

    public SettableIntFieldProperty(SettableBeanProperty src,
            BeanPropertyMutator mutator, int index)
    {
        super(src, mutator, index);
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableIntFieldProperty(del, _propertyMutator, _optimizedIndex);
    }
    
    @Override
    public SettableBeanProperty withMutator(BeanPropertyMutator mut) {
        return new SettableIntFieldProperty(delegate, mut, _optimizedIndex);
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
        if (!p.isExpectedNumberIntToken()) {
            delegate.deserializeAndSet(p, ctxt, bean);
            return;
        }
        final int v = p.getIntValue();
        try {
            _propertyMutator.intField(bean, _optimizedIndex, v);
        } catch (Throwable e) {
            _reportProblem(bean, v, e);
        }
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance)
        throws JacksonException
    {
        if (p.isExpectedNumberIntToken()) {
            return setAndReturn(instance, p.getIntValue());
        }
        return delegate.deserializeSetAndReturn(p, ctxt, instance);
    }    

    @Override
    public void set(Object bean, Object value)
    {
        // not optimal (due to boxing), but better than using reflection:
        final int v = ((Number) value).intValue();
        try {
            _propertyMutator.intField(bean, _optimizedIndex, v);
        } catch (Throwable e) {
            _reportProblem(bean, v, e);
        }
    }
}
