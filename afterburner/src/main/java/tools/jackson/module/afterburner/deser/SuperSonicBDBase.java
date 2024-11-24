package tools.jackson.module.afterburner.deser;

import java.util.*;

import tools.jackson.core.*;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.sym.PropertyNameMatcher;

import tools.jackson.databind.*;
import tools.jackson.databind.deser.*;
import tools.jackson.databind.deser.bean.BeanDeserializer;
import tools.jackson.databind.deser.bean.BeanPropertyMap;
import tools.jackson.databind.deser.bean.PropertyBasedCreator;
import tools.jackson.databind.deser.impl.UnwrappedPropertyHandler;

/**
 * Base class for implementations of bean deserializers that Afterburner
 * uses to replace standard {@link BeanDeserializer}.
 */
public abstract class SuperSonicBDBase
    extends BeanDeserializer
{
    /**
     * Names of properties being deserialized, in ordered they are
     * expected to have been written (as per serialization settings);
     * used for speculative order-based optimizations
     */
    protected final SerializedString[] _orderedPropertyNames;

    /**
     * Properties matching names in {@link #_orderedPropertyNames},
     * assigned after resolution when property instances are finalized.
     */
    protected SettableBeanProperty[] _orderedProperties;

    /*
    /**********************************************************************
    /* Life-cycle, construction, initialization
    /**********************************************************************
     */

    public SuperSonicBDBase(BeanDeserializer src,
            List<SettableBeanProperty> props)
    {
        super(src);
        final int len = props.size();
        _orderedPropertyNames = new SerializedString[len];
        for (int i = 0; i < len; ++i) {
            _orderedPropertyNames[i] = new SerializedString(props.get(i).getName());
        }
        // do NOT yet assign properties, they need to be ordered
    }

    protected SuperSonicBDBase(SuperSonicBDBase src,
            UnwrappedPropertyHandler unwrapHandler, PropertyBasedCreator propertyBasedCreator,
            BeanPropertyMap renamedProperties, boolean ignoreAllUnknown)
    {
        super(src, unwrapHandler, propertyBasedCreator, renamedProperties, ignoreAllUnknown);
        _orderedPropertyNames = src._orderedPropertyNames;
        _orderedProperties = src._orderedProperties;
    }

    // // // Sub-classes must provide these:
    
    //public JsonDeserializer<Object> unwrappingDeserializer(NameTransformer unwrapper);


    /*
    @Override
    public JsonDeserializer<Object> unwrappingDeserializer(NameTransformer unwrapper) {
        return new SuperSonicBDBase(this, unwrapper);
    }
    */

    // // Others, let's just leave as is; will not be optimized?
    
    //public BeanDeserializer withObjectIdReader(ObjectIdReader oir) {

    //public BeanDeserializer withIgnorableProperties(HashSet<String> ignorableProps)
    
    //protected BeanDeserializerBase asArrayDeserializer()
    
    /*
    /**********************************************************************
    /* BenaDeserializer overrides
    /**********************************************************************
     */

    /**
     * This method is overridden as we need to know expected order of
     * properties.
     */
    @Override
    public void resolve(DeserializationContext ctxt)
    {
        super.resolve(ctxt);
        // Ok, now; need to find actual property instances to go with order
        // defined based on property names.

        // 20-Sep-2014, tatu: As per [afterburner#43], use of `JsonTypeInfo.As.EXTERNAL_PROPERTY`
        //   will "hide" matching property, leading to no match below.
        //   But since we don't use optimized path if that case, let's just bail out.
        if ((_externalTypeIdHandler != null) || (_unwrappedPropertyHandler != null)) {
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
            throw new IllegalStateException("Afterburner internal error: BeanDeserializer for "
                    +_beanType+" has no properties that match expected ordering (should have "+len+") -- can not create optimized deserializer");
        }
        _orderedProperties = props.toArray(new SettableBeanProperty[0]);
    }

//    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException
//    public final Object deserialize(JsonParser p, DeserializationContext ctxt, Object bean) throws IOException;;
//    public final Object deserializeFromObject(JsonParser p, DeserializationContext ctxt) throws IOException;

    protected final Object _deserializeDisordered(JsonParser p, DeserializationContext ctxt,
            Object bean)
        throws JacksonException
    {
        for (int ix = p.currentNameMatch(_propNameMatcher); ; ix = p.nextNameMatch(_propNameMatcher)) {
            if (ix >= 0) {
                p.nextToken();
                SettableBeanProperty prop = _propsByIndex[ix];
                try {
                    prop.deserializeAndSet(p, ctxt, bean);
                } catch (Exception e) {
                    wrapAndThrow(e, bean, prop.getName(), ctxt);
                }
                continue;
            }
            if (ix == PropertyNameMatcher.MATCH_END_OBJECT) {
                return bean;
            }
            if (ix != PropertyNameMatcher.MATCH_UNKNOWN_NAME) {
                return _handleUnexpectedWithin(p, ctxt, bean);
            }
            p.nextToken();
            handleUnknownVanilla(p, ctxt, bean, p.currentName());
        }
    }
}
