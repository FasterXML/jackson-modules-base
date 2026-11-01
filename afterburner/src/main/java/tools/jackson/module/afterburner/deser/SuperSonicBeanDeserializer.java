package tools.jackson.module.afterburner.deser;

import java.util.*;

import tools.jackson.core.*;

import tools.jackson.databind.*;
import tools.jackson.databind.deser.*;
import tools.jackson.databind.deser.bean.BeanDeserializer;
import tools.jackson.databind.deser.bean.BeanPropertyMap;
import tools.jackson.databind.deser.bean.PropertyBasedCreator;
import tools.jackson.databind.deser.impl.UnwrappedPropertyHandler;
import tools.jackson.databind.util.NameTransformer;

public final class SuperSonicBeanDeserializer
    extends SuperSonicBDBase
{
    /*
    /**********************************************************
    /* Life-cycle, construction, initialization
    /**********************************************************
     */

    public SuperSonicBeanDeserializer(BeanDeserializer src, List<SettableBeanProperty> props)
    {
        super(src, props);
    }

    protected SuperSonicBeanDeserializer(SuperSonicBeanDeserializer src,
            UnwrappedPropertyHandler unwrapHandler, PropertyBasedCreator propertyBasedCreator,
            BeanPropertyMap renamedProperties, boolean ignoreAllUnknown)
    {
        super(src, unwrapHandler, propertyBasedCreator, renamedProperties, ignoreAllUnknown);
    }
    
    @Override
    public ValueDeserializer<Object> unwrappingDeserializer(DeserializationContext ctxt,
            NameTransformer transformer)
    {
        // NOTE: copied verbatim from `BeanDeserializer`

        if (_currentlyTransforming == transformer) { // from [databind#383]
            return this;
        }
        _currentlyTransforming = transformer;
        try {
            UnwrappedPropertyHandler uwHandler = _unwrappedPropertyHandler;
            if (uwHandler != null) {
                uwHandler = uwHandler.renameAll(ctxt, transformer);
            }
            PropertyBasedCreator pbCreator = _propertyBasedCreator;
            if (pbCreator != null) {
                pbCreator = pbCreator.renameAll(ctxt, transformer);
            }
            return new SuperSonicBeanDeserializer(this, uwHandler, pbCreator,
                    _beanProperties.renameAll(ctxt, transformer), true);
        } finally { _currentlyTransforming = null; }
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
    public Object deserialize(JsonParser p, DeserializationContext ctxt)
        throws JacksonException
    {
        if (!_vanillaProcessing || (_objectIdReader != null)) {
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
        p.assignCurrentValue(bean);
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
        if (!p.nextName(_orderedPropertyNames[0])) {
            return _deserializeDisordered(p, ctxt, bean);
        }
        _readAndSet(p, ctxt, bean, _orderedProperties[0]);
        if (!p.nextName(_orderedPropertyNames[1])) {
            return _deserializeDisordered(p, ctxt, bean);
        }
        _readAndSet(p, ctxt, bean, _orderedProperties[1]);
        if (!p.nextName(_orderedPropertyNames[2])) {
            return _deserializeDisordered(p, ctxt, bean);
        }
        _readAndSet(p, ctxt, bean, _orderedProperties[2]);
        if (!p.nextName(_orderedPropertyNames[3])) {
            return _deserializeDisordered(p, ctxt, bean);
        }
        _readAndSet(p, ctxt, bean, _orderedProperties[3]);

        for (int i = 4, len = _orderedProperties.length; i < len; ++i) {
            if (!p.nextName(_orderedPropertyNames[i])) { // miss...
                if (p.currentToken() == JsonToken.END_OBJECT) {
                    return bean;
                }
                // we likely point to FIELD_NAME still; offline
                return _deserializeDisordered(p, ctxt, bean);
            }
            _readAndSet(p, ctxt, bean, _orderedProperties[i]);
        }
        // also, need to ensure we get closing END_OBJECT...
        if (p.nextToken() != JsonToken.END_OBJECT) {
            return _deserializeDisordered(p, ctxt, bean);
        }
        return bean;
    }

    // much of below is cut'n pasted from BeanSerializer
    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt, Object bean)
        throws JacksonException
    {
        // The ordered-property fast path below is only safe when no view filtering,
        // non-standard creation or other non-vanilla features are in play; otherwise
        // fall back to the parent implementation which handles all of those.
        if (!_vanillaProcessing) {
            return super.deserialize(p, ctxt, bean);
        }
        // [databind#631]: Assign current value, to be accessible by custom serializers
        p.assignCurrentValue(bean);
        if (_injectables != null) {
            injectValues(ctxt, bean);
        }
        if (_unwrappedPropertyHandler != null) {
            return deserializeWithUnwrapped(p, ctxt, bean);
        }
        if (_externalTypeIdHandler != null) {
            return deserializeWithExternalTypeId(p, ctxt, bean);
        }
        // We also we have at least 6 properties, so roll out first few
        SettableBeanProperty prop = _orderedProperties[0];
        if (p.isExpectedStartObjectToken()) {
            if (!p.nextName(_orderedPropertyNames[0])) {
                return _deserializeDisordered(p, ctxt, bean);
            }
        } else if (!p.hasToken(JsonToken.PROPERTY_NAME)
                || !prop.getName().equals(p.currentName())) {
            return _deserializeDisordered(p, ctxt, bean);
        }
        _readAndSet(p, ctxt, bean, prop);
        if (!p.nextName(_orderedPropertyNames[1])) {
            return _deserializeDisordered(p, ctxt, bean);
        }
        _readAndSet(p, ctxt, bean, _orderedProperties[1]);
        if (!p.nextName(_orderedPropertyNames[2])) {
            return _deserializeDisordered(p, ctxt, bean);
        }
        _readAndSet(p, ctxt, bean, _orderedProperties[2]);
        if (!p.nextName(_orderedPropertyNames[3])) {
            return _deserializeDisordered(p, ctxt, bean);
        }
        _readAndSet(p, ctxt, bean, _orderedProperties[3]);

        // then rest of properties
        for (int i = 4, len = _orderedProperties.length; i < len; ++i) {
            if (!p.nextName(_orderedPropertyNames[i])) { // miss...
                if (p.hasToken(JsonToken.END_OBJECT)) {
                    return bean;
                }
                // we likely point to FIELD_NAME, so can just call parent impl
                return super.deserialize(p, ctxt, bean);
            }
            _readAndSet(p, ctxt, bean, _orderedProperties[i]);
        }
        // also, need to ensure we get closing END_OBJECT...
        if (p.nextToken() != JsonToken.END_OBJECT) {
            return super.deserialize(p, ctxt, bean);
        }
        return bean;
    }
}
