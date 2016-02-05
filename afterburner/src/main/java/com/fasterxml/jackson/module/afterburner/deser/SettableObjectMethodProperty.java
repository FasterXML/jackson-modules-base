package com.fasterxml.jackson.module.afterburner.deser;

import java.io.IOException;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

public final class SettableObjectMethodProperty
    extends OptimizedSettableBeanProperty<SettableObjectMethodProperty>
{
    private static final long serialVersionUID = 1L;

    public SettableObjectMethodProperty(SettableBeanProperty src,
            BeanPropertyMutator mutator, int index)
    {
        super(src, mutator, index);
    }

    public SettableObjectMethodProperty(SettableObjectMethodProperty src, JsonDeserializer<?> deser) {
        super(src, deser);
    }

    public SettableObjectMethodProperty(SettableObjectMethodProperty src, PropertyName name) {
        super(src, name);
    }

    @Override
    public SettableBeanProperty withName(PropertyName name) {
        return new SettableObjectMethodProperty(this, name);
    }

    @Override
    public SettableBeanProperty withValueDeserializer(JsonDeserializer<?> deser) {
        if (!_isDefaultDeserializer(deser)) {
            return _originalSettable.withValueDeserializer(deser);
        }
        return new SettableObjectMethodProperty(this, deser);
    }

    @Override
    public SettableBeanProperty withMutator(BeanPropertyMutator mut) {
        return new SettableObjectMethodProperty(_originalSettable, mut, _optimizedIndex);
    }

    /*
    /********************************************************************** 
    /* Deserialization
    /********************************************************************** 
     */
    
    @Override
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt,
            Object bean) throws IOException {
        set(bean, deserialize(p, ctxt));
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        _propertyMutator.objectSetter(bean, value);
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance) throws IOException {
        return setAndReturn(instance, deserialize(p, ctxt));
    }    
}
