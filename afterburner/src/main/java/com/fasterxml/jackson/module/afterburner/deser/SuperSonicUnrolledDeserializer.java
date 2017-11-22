package com.fasterxml.jackson.module.afterburner.deser;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.*;
import com.fasterxml.jackson.databind.deser.impl.BeanPropertyMap;
import com.fasterxml.jackson.databind.deser.impl.UnwrappedPropertyHandler;
import com.fasterxml.jackson.databind.util.NameTransformer;

/**
 * Optimized variant used for Beans with 1 to 6 properties; if so,
 * handling is unrolled to eliminate looping.
 */
public final class SuperSonicUnrolledDeserializer
    extends SuperSonicBDBase
{
    private static final long serialVersionUID = 1;

    protected final int _propCount;

    // // // We store separate references in form more easily accessed
    // // // from switch statement
    
    protected SerializedString _name1;
    protected SettableBeanProperty _prop1;

    protected SerializedString _name2;
    protected SettableBeanProperty _prop2;

    protected SerializedString _name3;
    protected SettableBeanProperty _prop3;

    protected SerializedString _name4;
    protected SettableBeanProperty _prop4;

    protected SerializedString _name5;
    protected SettableBeanProperty _prop5;

    protected SerializedString _name6;
    protected SettableBeanProperty _prop6;
    
    /*
    /**********************************************************
    /* Life-cycle, construction, initialization
    /**********************************************************
     */

    public SuperSonicUnrolledDeserializer(BeanDeserializer src, List<SettableBeanProperty> props)
    {
        super(src, props);
        _propCount = props.size();
    }

    protected SuperSonicUnrolledDeserializer(SuperSonicUnrolledDeserializer src,
            UnwrappedPropertyHandler unwrapHandler, BeanPropertyMap renamedProperties,
            boolean ignoreAllUnknown)
    {
        super(src, unwrapHandler, renamedProperties, ignoreAllUnknown);
        _propCount = src._propCount;

        _prop1 = src._prop1;
        _name1 = src._name1;
        _prop2 = src._prop2;
        _name2 = src._name2;
        _prop3 = src._prop3;
        _name3 = src._name3;
        _prop4 = src._prop4;
        _name4 = src._name4;
        _prop5 = src._prop5;
        _name5 = src._name5;
        _prop6 = src._prop6;
        _name6 = src._name6;
    }

    @Override
    public JsonDeserializer<Object> unwrappingDeserializer(DeserializationContext ctxt,
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
            return new SuperSonicUnrolledDeserializer(this, uwHandler,
                    _beanProperties.renameAll(ctxt, transformer), true);
        } finally { _currentlyTransforming = null; }
    }

    @Override
    public void resolve(DeserializationContext ctxt)
        throws JsonMappingException
    {
        super.resolve(ctxt);
        // 19-Oct-2017, tatu: Not sure why but seems to occur sometimes...
        if (_orderedProperties != null) {
            SettableBeanProperty[] oProps = new SettableBeanProperty[6];
            SerializedString[] oNames = new SerializedString[6];
            int offset = 6 - _propCount;
            System.arraycopy(_orderedProperties, 0, oProps, offset, _propCount);
            System.arraycopy(_orderedPropertyNames, 0, oNames, offset, _propCount);
            
            _prop1 = oProps[0];
            _name1 = oNames[0];
            _prop2 = oProps[1];
            _name2 = oNames[1];
            _prop3 = oProps[2];
            _name3 = oNames[2];
            _prop4 = oProps[3];
            _name4 = oNames[3];
            _prop5 = oProps[4];
            _name5 = oNames[4];
            _prop6 = oProps[5];
            _name6 = oNames[5];
        }
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
        SettableBeanProperty prop = null;

        try {
            switch (_propCount) {
            default:
            //case 6:
                prop = _prop1;
                if (!p.nextFieldName(_name1)) {
                    break;
                }
                p.nextToken();
                prop.deserializeAndSet(p, ctxt, bean);
                // fall through
            case 5:
                prop = _prop2;
                if (!p.nextFieldName(_name2)) {
                    break;
                }
                p.nextToken();
                prop.deserializeAndSet(p, ctxt, bean);
                // fall through
            case 4:
                prop = _prop3;
                if (!p.nextFieldName(_name3)) {
                    break;
                }
                p.nextToken();
                prop.deserializeAndSet(p, ctxt, bean);
                // fall through
            case 3:
                prop = _prop4;
                if (!p.nextFieldName(_name4)) {
                    break;
                }
                p.nextToken();
                prop.deserializeAndSet(p, ctxt, bean);
                // fall through
            case 2:
                prop = _prop5;
                if (!p.nextFieldName(_name5)) {
                    break;
                }
                p.nextToken();
                prop.deserializeAndSet(p, ctxt, bean);
                // fall through
            case 1:
                prop = _prop6;
                if (!p.nextFieldName(_name6)) {
                    break;
                }
                p.nextToken();
                prop.deserializeAndSet(p, ctxt, bean);
                // also, need to ensure we get closing END_OBJECT...
                if (p.nextToken() == JsonToken.END_OBJECT) {
                    return bean;
                }
            }
        } catch (Exception e) {
            wrapAndThrow(e, bean, prop.getName(), ctxt);
        }
        // may have encountered pre-mature END_OBJECT too
        if (p.currentToken() == JsonToken.END_OBJECT) {
            return bean;
        }
        return _deserializeDisordered(p, ctxt, bean);
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
        // then rest of properties. NOTE! Off-by-one, to skip one we did above
        try {
            switch (_propCount) {
            default:
            //case 6:
                prop = _prop2;
                if (!p.nextFieldName(_name2)) {
                    break;
                }
                p.nextToken();
                prop.deserializeAndSet(p, ctxt, bean);
                // fall through
            case 5:
                prop = _prop3;
                if (!p.nextFieldName(_name3)) {
                    break;
                }
                p.nextToken();
                prop.deserializeAndSet(p, ctxt, bean);
                // fall through
            case 4:
                prop = _prop4;
                if (!p.nextFieldName(_name4)) {
                    break;
                }
                p.nextToken();
                prop.deserializeAndSet(p, ctxt, bean);
                // fall through
            case 3:
                prop = _prop5;
                if (!p.nextFieldName(_name5)) {
                    break;
                }
                p.nextToken();
                prop.deserializeAndSet(p, ctxt, bean);
                // fall through
            case 2:
                prop = _prop6;
                if (!p.nextFieldName(_name6)) {
                    break;
                }
                p.nextToken();
                prop.deserializeAndSet(p, ctxt, bean);
                // fall through
            case 1:
                // NOTE! skip "last" one to compensate for OBO
                // But we still need to ensure we get closing END_OBJECT...
                if (p.nextToken() == JsonToken.END_OBJECT) {
                    return bean;
                }
            }
        } catch (Exception e) {
            wrapAndThrow(e, bean, prop.getName(), ctxt);
        }
        // may have encountered pre-mature END_OBJECT too
        if (p.currentToken() == JsonToken.END_OBJECT) {
            return bean;
        }
        return _deserializeDisordered(p, ctxt, bean);
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
                return _deserializeDisordered(p, ctxt, bean);
            }
        } else if (!p.hasToken(JsonToken.FIELD_NAME)
                || !prop.getName().equals(p.currentName())) {
            return _deserializeDisordered(p, ctxt, bean);
        }
        // and deserialize
        p.nextToken();
        try {
            prop.deserializeAndSet(p, ctxt, bean);
        } catch (Exception e) {
            wrapAndThrow(e, bean, prop.getName(), ctxt);
        }

        // then rest of properties. NOTE! Off-by-one, to skip one we did above
        try {
            switch (_propCount) {
            default:
            //case 6:
                prop = _prop2;
                if (!p.nextFieldName(_name2)) {
                    break;
                }
                p.nextToken();
                prop.deserializeAndSet(p, ctxt, bean);
                // fall through
            case 5:
                prop = _prop3;
                if (!p.nextFieldName(_name3)) {
                    break;
                }
                p.nextToken();
                prop.deserializeAndSet(p, ctxt, bean);
                // fall through
            case 4:
                prop = _prop4;
                if (!p.nextFieldName(_name4)) {
                    break;
                }
                p.nextToken();
                prop.deserializeAndSet(p, ctxt, bean);
                // fall through
            case 3:
                prop = _prop5;
                if (!p.nextFieldName(_name5)) {
                    break;
                }
                p.nextToken();
                prop.deserializeAndSet(p, ctxt, bean);
                // fall through
            case 2:
                prop = _prop6;
                if (!p.nextFieldName(_name6)) {
                    break;
                }
                p.nextToken();
                prop.deserializeAndSet(p, ctxt, bean);
                // fall through
            case 1:
                // NOTE! skip "last" one to compensate for OBO
                // But we still need to ensure we get closing END_OBJECT...
                if (p.nextToken() == JsonToken.END_OBJECT) {
                    return bean;
                }
            }
        } catch (Exception e) {
            wrapAndThrow(e, bean, prop.getName(), ctxt);
        }
        // may have encountered pre-mature END_OBJECT too
        if (p.currentToken() == JsonToken.END_OBJECT) {
            return bean;
        }
        return _deserializeDisordered(p, ctxt, bean);
    }
}
