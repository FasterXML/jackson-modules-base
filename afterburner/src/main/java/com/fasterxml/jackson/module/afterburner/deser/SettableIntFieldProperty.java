package com.fasterxml.jackson.module.afterburner.deser;

import java.io.IOException;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

public final class SettableIntFieldProperty
    extends OptimizedSettableBeanProperty<SettableIntFieldProperty>
{
    private static final long serialVersionUID = 1L;

    public SettableIntFieldProperty(SettableBeanProperty src,
            BeanPropertyMutator mutator, int index)
    {
        super(src, mutator, index);
    }

    public SettableIntFieldProperty(SettableIntFieldProperty src,
            JsonDeserializer<?> deser)
    {
        super(src, deser);
    }

    public SettableIntFieldProperty(SettableIntFieldProperty src, PropertyName name) {
        super(src, name);
    }
    
    @Override
    public SettableBeanProperty withName(PropertyName name) {
        return new SettableIntFieldProperty(this, name);
    }
    
    @Override
    public SettableBeanProperty withValueDeserializer(JsonDeserializer<?> deser) {
        if (!_isDefaultDeserializer(deser)) {
            return _originalSettable.withValueDeserializer(deser);
        }
        return new SettableIntFieldProperty(this, deser);
    }
    
    @Override
    public SettableBeanProperty withMutator(BeanPropertyMutator mut) {
        return new SettableIntFieldProperty(_originalSettable, mut, _optimizedIndex);
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
        _propertyMutator.intField(bean, v);
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        // not optimal (due to boxing), but better than using reflection:
        _propertyMutator.intField(bean, ((Number) value).intValue());
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance) throws IOException
    {
        int v = p.hasToken(JsonToken.VALUE_NUMBER_INT) ? p.getIntValue() : _deserializeInt(p, ctxt);
        return setAndReturn(instance, v);
    }    
}
