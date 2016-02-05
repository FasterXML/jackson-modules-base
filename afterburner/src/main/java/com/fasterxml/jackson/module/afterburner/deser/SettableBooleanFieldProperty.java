package com.fasterxml.jackson.module.afterburner.deser;

import java.io.IOException;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

public final class SettableBooleanFieldProperty
    extends OptimizedSettableBeanProperty<SettableBooleanFieldProperty>
{
    private static final long serialVersionUID = 1L;

    public SettableBooleanFieldProperty(SettableBeanProperty src,
            BeanPropertyMutator mutator, int index)
    {
        super(src, mutator, index);
    }

    public SettableBooleanFieldProperty(SettableBooleanFieldProperty src,
            JsonDeserializer<?> deser)
    {
        super(src, deser);
    }

    public SettableBooleanFieldProperty(SettableBooleanFieldProperty src, PropertyName name) {
        super(src, name);
    }
    
    @Override
    public SettableBeanProperty withName(PropertyName name) {
        return new SettableBooleanFieldProperty(this, name);
    }

    @Override
    public SettableBeanProperty withValueDeserializer(JsonDeserializer<?> deser) {
        if (!_isDefaultDeserializer(deser)) {
            return _originalSettable.withValueDeserializer(deser);
        }
        return new SettableBooleanFieldProperty(this, deser);
    }

    @Override
    public SettableBeanProperty withMutator(BeanPropertyMutator mut) {
        return new SettableBooleanFieldProperty(_originalSettable, mut, _optimizedIndex);
    }

    /*
    /********************************************************************** 
    /* Deserialization
    /********************************************************************** 
     */

    @Override
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt, Object bean) throws IOException {
        boolean b;
        JsonToken t = p.getCurrentToken();
        if (t == JsonToken.VALUE_TRUE) {
            b = true;
        } else if (t == JsonToken.VALUE_FALSE) {
            b = false;
        } else {
            b = _deserializeBoolean(p, ctxt);
        }
        _propertyMutator.booleanField(bean, b);
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        // not optimal (due to boxing), but better than using reflection:
        _propertyMutator.booleanField(bean, ((Boolean) value).booleanValue());
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance) throws IOException
    {
        boolean b;
        JsonToken t = p.getCurrentToken();
        if (t == JsonToken.VALUE_TRUE) {
            b = true;
        } else if (t == JsonToken.VALUE_FALSE) {
            b = false;
        } else {
            b = _deserializeBoolean(p, ctxt);
        }
        return setAndReturn(instance, b);
    }    
}
