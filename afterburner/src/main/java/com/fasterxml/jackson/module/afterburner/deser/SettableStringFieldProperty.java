package com.fasterxml.jackson.module.afterburner.deser;

import tools.jackson.core.*;

import tools.jackson.databind.*;
import tools.jackson.databind.deser.SettableBeanProperty;

public final class SettableStringFieldProperty
    extends OptimizedSettableBeanProperty<SettableStringFieldProperty>
{
    private static final long serialVersionUID = 1L;

    public SettableStringFieldProperty(SettableBeanProperty src,
            BeanPropertyMutator mutator, int index)
    {
        super(src, mutator, index);
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableStringFieldProperty(del, _propertyMutator, _optimizedIndex);
    }

    @Override
    public SettableBeanProperty withMutator(BeanPropertyMutator mut) {
        return new SettableStringFieldProperty(delegate, mut, _optimizedIndex);
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
        if (!p.hasToken(JsonToken.VALUE_STRING)) {
            delegate.deserializeAndSet(p, ctxt, bean);
            return;
        }
        final String text = p.getText();
        try {
            _propertyMutator.stringField(bean, _optimizedIndex, text);
        } catch (Throwable e) {
            _reportProblem(bean, text, e);
        }
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p, DeserializationContext ctxt, Object instance)
        throws JacksonException
    {
        if (p.hasToken(JsonToken.VALUE_STRING)) {
            return setAndReturn(instance, p.getText());
        }
        return delegate.deserializeSetAndReturn(p, ctxt, instance);
    }

    @Override
    public void set(Object bean, Object value)
    {
        final String text = (String) value;
        try {
            _propertyMutator.stringField(bean, _optimizedIndex, text);
        } catch (Throwable e) {
            _reportProblem(bean, text, e);
        }
    }
}
