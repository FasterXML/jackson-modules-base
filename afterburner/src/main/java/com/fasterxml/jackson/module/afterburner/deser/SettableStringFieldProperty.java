package com.fasterxml.jackson.module.afterburner.deser;

import java.io.IOException;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

public final class SettableStringFieldProperty
    extends OptimizedSettableBeanProperty<SettableStringFieldProperty>
{
    private static final long serialVersionUID = 1L;

    public SettableStringFieldProperty(SettableBeanProperty src,
            BeanPropertyMutator mutator, int index)
    {
        super(src, mutator, index);
    }

    public SettableStringFieldProperty(SettableStringFieldProperty src, JsonDeserializer<?> deser) {
        super(src, deser);
    }

    public SettableStringFieldProperty(SettableStringFieldProperty src, PropertyName name) {
        super(src, name);
    }
    
    @Override
    public SettableBeanProperty withName(PropertyName name) {
        return new SettableStringFieldProperty(this, name);
    }

    @Override
    public SettableBeanProperty withValueDeserializer(JsonDeserializer<?> deser) {
        if (!_isDefaultDeserializer(deser)) {
            return _originalSettable.withValueDeserializer(deser);
        }
        return new SettableStringFieldProperty(this, deser);
    }
    
    @Override
    public SettableBeanProperty withMutator(BeanPropertyMutator mut) {
        return new SettableStringFieldProperty(_originalSettable, mut, _optimizedIndex);
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
        String text = p.getValueAsString();
        if (text == null) {
            text = _deserializeString(p, ctxt);
        }
        _propertyMutator.stringField(bean, text);
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        _propertyMutator.stringField(bean, (String) value);
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p, DeserializationContext ctxt, Object instance)
        throws IOException
    {
        String text = p.getValueAsString();
        if (text == null) {
            text = _deserializeString(p, ctxt);
        }
        return setAndReturn(instance, text);
    }
}
