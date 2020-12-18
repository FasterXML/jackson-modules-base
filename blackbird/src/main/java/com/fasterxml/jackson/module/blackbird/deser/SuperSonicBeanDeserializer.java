package com.fasterxml.jackson.module.blackbird.deser;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.SerializedString;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.*;
import com.fasterxml.jackson.databind.deser.impl.BeanPropertyMap;
import com.fasterxml.jackson.databind.deser.impl.UnwrappedPropertyHandler;
import com.fasterxml.jackson.databind.util.NameTransformer;

final class SuperSonicBeanDeserializer extends BeanDeserializer
{
    /**
     * Names of properties being deserialized, in ordered they are
     * expected to have been written (as per serialization settings);
     * used for speculative order-based optimizations
     */
    protected final SerializedString[] _orderedPropertyNames;

    /**
<<<<<<< HEAD
     * Properties matching names in {@code _orderedPropertyNames},
=======
     * Properties matching names in {@code #_orderedPropertyNames},
>>>>>>> 2.12
     * assigned after resolution when property instances are finalized.
     */
    protected SettableBeanProperty[] _orderedProperties;

    /*
    /**********************************************************
    /* Life-cycle, construction, initialization
    /**********************************************************
     */

    public SuperSonicBeanDeserializer(BeanDeserializer src, List<SettableBeanProperty> props)
    {
        super(src);
        final int len = props.size();
        _orderedPropertyNames = new SerializedString[len];
        for (int i = 0; i < len; ++i) {
            _orderedPropertyNames[i] = new SerializedString(props.get(i).getName());
        }
    }

    protected SuperSonicBeanDeserializer(SuperSonicBeanDeserializer src,
            UnwrappedPropertyHandler unwrapHandler, BeanPropertyMap renamedProperties,
            boolean ignoreAllUnknown)
    {
        super(src, unwrapHandler, renamedProperties, ignoreAllUnknown);
        _orderedPropertyNames = src._orderedPropertyNames;
        _orderedProperties = src._orderedProperties;
    }

    @Override
    public JsonDeserializer<Object> unwrappingDeserializer(DeserializationContext ctxt,
            NameTransformer transformer)
    {
        // 17-Dec-2020, tatu: Was like so:
//        return new SuperSonicBeanDeserializer(this, unwrapper);
        // but Afterburner 3.0 had this:

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
            return new SuperSonicBeanDeserializer(this, uwHandler,
                    _beanProperties.renameAll(ctxt, transformer), true);
        } finally { _currentlyTransforming = null; }
    }

    // // Others, let's just leave as is; will not be optimized?

    //public BeanDeserializer withObjectIdReader(ObjectIdReader oir) {

    //public BeanDeserializer withIgnorableProperties(HashSet<String> ignorableProps)

    //protected BeanDeserializerBase asArrayDeserializer()

    /*
    /**********************************************************
    /* BenaDeserializer overrides
    /**********************************************************
     */

    /**
     * This method is overridden as we need to know expected order of
     * properties.
     */
    @Override
    public void resolve(DeserializationContext ctxt)
        throws JsonMappingException
    {
        super.resolve(ctxt);
        // Ok, now; need to find actual property instances to go with order
        // defined based on property names.

        // 20-Sep-2014, tatu: As per [afterburner#43], use of `JsonTypeInfo.As.EXTERNAL_PROPERTY`
        //   will "hide" matching property, leading to no match below.
        //   But since we don't use optimized path if that case, let's just bail out.
        if (_externalTypeIdHandler != null || _unwrappedPropertyHandler != null) {
            // should we assign empty array or... ?
            return;
        }

        int len = _orderedPropertyNames.length;
        ArrayList<SettableBeanProperty> props = new ArrayList<SettableBeanProperty>(len);
        int i = 0;
        for (; i < len; ++i) {
            SettableBeanProperty prop = _beanProperties.findDefinition(_orderedPropertyNames[i].toString());
            if (prop != null) {
                props.add(prop);
            }
        }
        // should usually get at least one property; let's for now consider it an error if not
        // (may need to revisit in future)
        if (i == 0) {
            throw new IllegalStateException("Blackbird internal error: BeanDeserializer for "
                    +_beanType+" has no properties that match expected ordering (should have "+len+") -- can not create optimized deserializer");
        }
        _orderedProperties = props.toArray(new SettableBeanProperty[0]);
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException
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
        for (int i = 0, len = _orderedProperties.length; i < len; ++i) {
            SettableBeanProperty prop = _orderedProperties[i];
            if (!p.nextFieldName(_orderedPropertyNames[i])) { // miss...
                if (p.currentToken() == JsonToken.END_OBJECT) {
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

    // much of below is cut'n pasted from BeanSerializer
    @Override
    public final Object deserialize(JsonParser p, DeserializationContext ctxt, Object bean)
        throws IOException
    {
        // [databind#631]: Assign current value, to be accessible by custom deserializers
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
                || !prop.getName().equals(p.currentName())) {
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
        // See BeanDeserializer.deserializeFromObject [databind#622]
        // Allow Object Id references to come in as JSON Objects as well...
        if ((_objectIdReader != null) && _objectIdReader.maySerializeAsObject()) {
            if (p.hasTokenId(JsonTokenId.ID_FIELD_NAME)
                    && _objectIdReader.isValidReferencePropertyName(p.currentName(), p)) {
                return deserializeFromObjectId(p, ctxt);
            }
        }
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
                || !prop.getName().equals(p.currentName())) {
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
