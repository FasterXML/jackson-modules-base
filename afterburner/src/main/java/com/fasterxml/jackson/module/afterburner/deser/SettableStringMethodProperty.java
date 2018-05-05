package com.fasterxml.jackson.module.afterburner.deser;

import java.io.IOException;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

public final class SettableStringMethodProperty
    extends OptimizedSettableBeanProperty<SettableStringMethodProperty>
{
    private static final long serialVersionUID = 1L;

    public SettableStringMethodProperty(SettableBeanProperty src,
            BeanPropertyMutator mutator, int index)
    {
        super(src, mutator, index);
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableStringMethodProperty(del, _propertyMutator, _optimizedIndex);
    }

    @Override
    public SettableBeanProperty withMutator(BeanPropertyMutator mut) {
        return new SettableStringMethodProperty(delegate, mut, _optimizedIndex);
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
        String text;
        if (p.hasToken(JsonToken.VALUE_NULL)) {
            if (_skipNulls) {
                return;
            }
            text = (String) _nullProvider.getNullValue(ctxt);
        } else {
            text = p.getValueAsString();
            if (text == null) {
                text = _deserializeString(p, ctxt);
            }
        }
        try {
            _propertyMutator.stringSetter(bean, _optimizedIndex, text);
        } catch (Throwable e) {
            _reportProblem(bean, text, e);
        }
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p, DeserializationContext ctxt, Object instance) throws IOException
    {
        String text;
        if (p.hasToken(JsonToken.VALUE_NULL)) {
            if (_skipNulls) {
                return instance;
            }
            text = (String) _nullProvider.getNullValue(ctxt);
        } else {
            text = p.getValueAsString();
            if (text == null) {
                text = _deserializeString(p, ctxt);
            }
        }
        return setAndReturn(instance, text);
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        final String text = (String) value;
        try {
            _propertyMutator.stringSetter(bean, _optimizedIndex, text);
        } catch (Throwable e) {
            _reportProblem(bean, text, e);
        }
    }
}
