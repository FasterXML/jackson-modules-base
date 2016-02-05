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

    public SettableStringMethodProperty(SettableStringMethodProperty src, JsonDeserializer<?> deser) {
        super(src, deser);
    }

    public SettableStringMethodProperty(SettableStringMethodProperty src, PropertyName name) {
        super(src, name);
    }
    
    @Override
    public SettableBeanProperty withName(PropertyName name) {
        return new SettableStringMethodProperty(this, name);
    }
    
    @Override
    public SettableBeanProperty withValueDeserializer(JsonDeserializer<?> deser) {
        if (!_isDefaultDeserializer(deser)) {
            return _originalSettable.withValueDeserializer(deser);
        }
        return new SettableStringMethodProperty(this, deser);
    }
    
    @Override
    public SettableBeanProperty withMutator(BeanPropertyMutator mut) {
        return new SettableStringMethodProperty(_originalSettable, mut, _optimizedIndex);
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
        _propertyMutator.stringSetter(bean, text);
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        _propertyMutator.stringSetter(bean, (String) value);
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
