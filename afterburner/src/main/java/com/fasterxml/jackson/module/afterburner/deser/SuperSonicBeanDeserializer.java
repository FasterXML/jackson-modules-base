package com.fasterxml.jackson.module.afterburner.deser;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.*;
import com.fasterxml.jackson.databind.util.NameTransformer;

public final class SuperSonicBeanDeserializer
    extends SuperSonicBDBase
{
    private static final long serialVersionUID = 1;

    /*
    /**********************************************************
    /* Life-cycle, construction, initialization
    /**********************************************************
     */

    public SuperSonicBeanDeserializer(BeanDeserializer src, List<SettableBeanProperty> props)
    {
        super(src, props);
    }

    protected SuperSonicBeanDeserializer(SuperSonicBeanDeserializer src, NameTransformer unwrapper)
    {
        super(src, unwrapper);
    }
    
    @Override
    public JsonDeserializer<Object> unwrappingDeserializer(NameTransformer unwrapper) {
        return new SuperSonicBeanDeserializer(this, unwrapper);
    }

    // // Others, let's just leave as is; will not be optimized?
    
    //public BeanDeserializer withObjectIdReader(ObjectIdReader oir) {

    //public BeanDeserializer withIgnorableProperties(HashSet<String> ignorableProps)
    
    //protected BeanDeserializerBase asArrayDeserializer()
    
    /*
    /**********************************************************
    /* Deserialization method implementations
    /**********************************************************
     */

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException
    {
        if (!_vanillaProcessing || _objectIdReader != null) {
            // should we ever get here? Just in case
            return super.deserialize(p, ctxt);
        }
        // common case first:
        if (!p.isExpectedStartObjectToken()) {
            return _deserializeOther(p, ctxt, p.currentToken());
        }
        if (_nonStandardCreation) {
            p.nextToken();
            if (_unwrappedPropertyHandler != null) {
                return deserializeWithUnwrapped(p, ctxt);
            }
            if (_externalTypeIdHandler != null) {
                return deserializeWithExternalTypeId(p, ctxt);
            }
            Object bean = deserializeFromObjectUsingNonDefault(p, ctxt);
            if (_injectables != null) {
                injectValues(ctxt, bean);
            }
            return bean;
        }
        final Object bean = _valueInstantiator.createUsingDefault(ctxt);
        // [databind#631]: Assign current value, to be accessible by custom serializers
        p.setCurrentValue(bean);
        if (p.canReadObjectId()) {
            Object id = p.getObjectId();
            if (id != null) {
                _handleTypedObjectId(p, ctxt, bean, id);
            }
        }
        if (_injectables != null) {
            injectValues(ctxt, bean);
        }

        // We also we have at least 6 properties, so roll out first few
        SettableBeanProperty prop = _orderedProperties[0];
        if (!p.nextFieldName(_orderedPropertyNames[0])) {
            return _deserializeDisordered(p, ctxt, bean);
        }
        p.nextToken();
        try {
            prop.deserializeAndSet(p, ctxt, bean);
        } catch (Exception e) {
            wrapAndThrow(e, bean, prop.getName(), ctxt);
        }

        prop = _orderedProperties[1];
        if (!p.nextFieldName(_orderedPropertyNames[1])) {
            return _deserializeDisordered(p, ctxt, bean);
        }
        p.nextToken();
        try {
            prop.deserializeAndSet(p, ctxt, bean);
        } catch (Exception e) {
            wrapAndThrow(e, bean, prop.getName(), ctxt);
        }
        
        prop = _orderedProperties[2];
        if (!p.nextFieldName(_orderedPropertyNames[2])) {
            return _deserializeDisordered(p, ctxt, bean);
        }
        p.nextToken();
        try {
            prop.deserializeAndSet(p, ctxt, bean);
        } catch (Exception e) {
            wrapAndThrow(e, bean, prop.getName(), ctxt);
        }

        prop = _orderedProperties[3];
        if (!p.nextFieldName(_orderedPropertyNames[3])) {
            return _deserializeDisordered(p, ctxt, bean);
        }
        p.nextToken();
        try {
            prop.deserializeAndSet(p, ctxt, bean);
        } catch (Exception e) {
            wrapAndThrow(e, bean, prop.getName(), ctxt);
        }
        
        for (int i = 4, len = _orderedProperties.length; i < len; ++i) {
            prop = _orderedProperties[i];
            if (!p.nextFieldName(_orderedPropertyNames[i])) { // miss...
                if (p.currentToken() == JsonToken.END_OBJECT) {
                    return bean;
                }
                // we likely point to FIELD_NAME still; offline
                return _deserializeDisordered(p, ctxt, bean);
            }
            p.nextToken(); // skip field, returns value token
            try {
                prop.deserializeAndSet(p, ctxt, bean);
            } catch (Exception e) {
                wrapAndThrow(e, bean, prop.getName(), ctxt);
            }
        }
        // also, need to ensure we get closing END_OBJECT...
        if (p.nextToken() != JsonToken.END_OBJECT) {
            return _deserializeDisordered(p, ctxt, bean);
        }
        return bean;
    }

    // much of below is cut'n pasted from BeanSerializer
    @Override
    public final Object deserialize(JsonParser p, DeserializationContext ctxt,
            Object bean) throws IOException
    {
        // [databind#631]: Assign current value, to be accessible by custom serializers
        p.setCurrentValue(bean);
        if (_injectables != null) {
            injectValues(ctxt, bean);
        }
        if (_unwrappedPropertyHandler != null) {
            return deserializeWithUnwrapped(p, ctxt, bean);
        }
        if (_externalTypeIdHandler != null) {
            return deserializeWithExternalTypeId(p, ctxt, bean);
        }
        SettableBeanProperty prop = _orderedProperties[0];
        // First: verify that first name is expected
        if (p.isExpectedStartObjectToken()) {
            if (!p.nextFieldName(_orderedPropertyNames[0])) {
                return super.deserialize(p,  ctxt, bean);
            }
        } else if (!p.hasToken(JsonToken.FIELD_NAME)
                || !prop.getName().equals(p.getCurrentName())) {
            // no, something funky, use base impl for special cases
            return super.deserialize(p,  ctxt, bean);
        }
        p.nextToken();
        try {
            prop.deserializeAndSet(p, ctxt, bean);
        } catch (Exception e) {
            wrapAndThrow(e, bean, prop.getName(), ctxt);
        }
        
        // then rest of properties
        for (int i = 1, len = _orderedProperties.length; i < len; ++i) {
            if (!p.nextFieldName(_orderedPropertyNames[i])) { // miss...
                if (p.hasToken(JsonToken.END_OBJECT)) {
                    return bean;
                }
                // we likely point to FIELD_NAME, so can just call parent impl
                return super.deserialize(p, ctxt, bean);
            }
            prop = _orderedProperties[i];
            p.nextToken(); // skip field, returns value token
            try {
                prop.deserializeAndSet(p, ctxt, bean);
            } catch (Exception e) {
                wrapAndThrow(e, bean, prop.getName(), ctxt);
            }
        }
        // also, need to ensure we get closing END_OBJECT...
        if (p.nextToken() != JsonToken.END_OBJECT) {
            return super.deserialize(p, ctxt, bean);
        }
        return bean;
    }

    // much of below is cut'n pasted from BeanSerializer
    @Override
    public final Object deserializeFromObject(JsonParser p, DeserializationContext ctxt) throws IOException
    {
        if (_nonStandardCreation) {
            if (_unwrappedPropertyHandler != null) {
                return deserializeWithUnwrapped(p, ctxt);
            }
            if (_externalTypeIdHandler != null) {
                return deserializeWithExternalTypeId(p, ctxt);
            }
            Object bean = deserializeFromObjectUsingNonDefault(p, ctxt);
            if (_injectables != null) {
                injectValues(ctxt, bean);
            }
            return bean;
        }
        final Object bean = _valueInstantiator.createUsingDefault(ctxt);
        // [databind#631]: Assign current value, to be accessible by custom serializers
        p.setCurrentValue(bean);
        if (p.canReadObjectId()) {
            Object id = p.getObjectId();
            if (id != null) {
                _handleTypedObjectId(p, ctxt, bean, id);
            }
        }
        if (_injectables != null) {
            injectValues(ctxt, bean);
        }
        SettableBeanProperty prop = _orderedProperties[0];
        // First: verify that first name is expected
        if (p.isExpectedStartObjectToken()) {
            if (!p.nextFieldName(_orderedPropertyNames[0])) {
                return super.deserialize(p,  ctxt, bean);
            }
        } else if (!p.hasToken(JsonToken.FIELD_NAME)
                || !prop.getName().equals(p.getCurrentName())) {
            return super.deserialize(p,  ctxt, bean);
        }
        // and deserialize
        p.nextToken();
        try {
            prop.deserializeAndSet(p, ctxt, bean);
        } catch (Exception e) {
            wrapAndThrow(e, bean, prop.getName(), ctxt);
        }

        // then rest of properties
        for (int i = 1, len = _orderedProperties.length; i < len; ++i) {
            prop = _orderedProperties[i];
            if (!p.nextFieldName(_orderedPropertyNames[i])) { // miss...
                if (p.hasToken(JsonToken.END_OBJECT)) {
                    return bean;
                }
                // we likely point to FIELD_NAME, so can just call parent impl
                return super.deserialize(p, ctxt, bean);
            }
            p.nextToken(); // skip field, returns value token
            try {
                prop.deserializeAndSet(p, ctxt, bean);
            } catch (Exception e) {
                wrapAndThrow(e, bean, prop.getName(), ctxt);
            }
        }
        // also, need to ensure we get closing END_OBJECT...
        if (p.nextToken() != JsonToken.END_OBJECT) {
            return super.deserialize(p, ctxt, bean);
        }
        return bean;
    }
}
