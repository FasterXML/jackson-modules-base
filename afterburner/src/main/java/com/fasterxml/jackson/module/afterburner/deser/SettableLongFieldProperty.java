package com.fasterxml.jackson.module.afterburner.deser;

import java.io.IOException;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

public final class SettableLongFieldProperty
    extends OptimizedSettableBeanProperty<SettableLongFieldProperty>
{
    private static final long serialVersionUID = 1L;

    public SettableLongFieldProperty(SettableBeanProperty src,
            BeanPropertyMutator mutator, int index)
    {
        super(src, mutator, index);
    }

    public SettableLongFieldProperty(SettableLongFieldProperty src, JsonDeserializer<?> deser) {
        super(src, deser);
    }

    public SettableLongFieldProperty(SettableLongFieldProperty src, PropertyName name) {
        super(src, name);
    }
    
    @Override
    public SettableBeanProperty withName(PropertyName name) {
        return new SettableLongFieldProperty(this, name);
    }
    
    @Override
    public SettableBeanProperty withValueDeserializer(JsonDeserializer<?> deser) {
        if (!_isDefaultDeserializer(deser)) {
            return _originalSettable.withValueDeserializer(deser);
        }
        return new SettableLongFieldProperty(this, deser);
    }
    
    @Override
    public SettableBeanProperty withMutator(BeanPropertyMutator mut) {
        return new SettableLongFieldProperty(_originalSettable, mut, _optimizedIndex);
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
        long l = p.hasToken(JsonToken.VALUE_NUMBER_INT) ? p.getLongValue() : _deserializeLong(p, ctxt);
        _propertyMutator.longField(bean, l);
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        // not optimal (due to boxing), but better than using reflection:
        _propertyMutator.longField(bean, ((Number) value).longValue());
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance) throws IOException
    {
        long l = p.hasToken(JsonToken.VALUE_NUMBER_INT) ? p.getLongValue() : _deserializeLong(p, ctxt);
        return setAndReturn(instance, l);
    }    
}
