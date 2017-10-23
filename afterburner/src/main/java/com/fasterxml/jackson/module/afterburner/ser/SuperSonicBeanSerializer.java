package com.fasterxml.jackson.module.afterburner.ser;

import java.io.IOException;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializer;
import com.fasterxml.jackson.databind.ser.impl.BeanAsArraySerializer;
import com.fasterxml.jackson.databind.ser.impl.ObjectIdWriter;
import com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanSerializer;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.fasterxml.jackson.databind.util.NameTransformer;

/**
 * @since 3.0
 */
public final class SuperSonicBeanSerializer extends BeanSerializerBase
{
    private static final long serialVersionUID = 1L;

    protected final int _propCount;

    // // // We store separate references in form more easily accessed
    // // // from switch statement

    protected BeanPropertyWriter _prop1;
    protected BeanPropertyWriter _prop2;
    protected BeanPropertyWriter _prop3;
    protected BeanPropertyWriter _prop4;
    protected BeanPropertyWriter _prop5;
    protected BeanPropertyWriter _prop6;

    /*
    /**********************************************************
    /* Life-cycle: constructors, factory methods
    /**********************************************************
     */
    
    public SuperSonicBeanSerializer(BeanSerializer base) {
        super(base);
        _propCount = _props.length;
        _calcUnrolled();
    }

    protected SuperSonicBeanSerializer(SuperSonicBeanSerializer src,
            ObjectIdWriter objectIdWriter)
    {
        super(src, objectIdWriter);
        _propCount = _props.length;
        _copyUnrolled(src);
    }

    protected SuperSonicBeanSerializer(SuperSonicBeanSerializer src,
            ObjectIdWriter objectIdWriter, Object filterId)
    {
        super(src, objectIdWriter, filterId);
        _propCount = _props.length;
        _copyUnrolled(src);
    }

    protected SuperSonicBeanSerializer(SuperSonicBeanSerializer src, Set<String> toIgnore)
    {
        super(src, toIgnore);
        _propCount = _props.length;
        // seems like set of properties might well change so...
        _calcUnrolled();
    }

    private void _calcUnrolled() {
        BeanPropertyWriter[] oProps = new BeanPropertyWriter[6];
        int offset = 6 - _propCount;
        System.arraycopy(_props, 0, oProps, offset, _propCount);
        
        _prop1 = oProps[0];
        _prop2 = oProps[1];
        _prop3 = oProps[2];
        _prop4 = oProps[3];
        _prop5 = oProps[4];
        _prop6 = oProps[5];
    }
    
    private void _copyUnrolled(SuperSonicBeanSerializer src) {
        _prop1 = src._prop1;
        _prop2 = src._prop2;
        _prop3 = src._prop3;
        _prop4 = src._prop4;
        _prop5 = src._prop5;
        _prop6 = src._prop6;
    }

    @Override
    public JsonSerializer<Object> unwrappingSerializer(NameTransformer unwrapper) {
        return new UnwrappingBeanSerializer(this, unwrapper);
    }

    @Override
    public BeanSerializerBase withObjectIdWriter(ObjectIdWriter objectIdWriter) {
        return new SuperSonicBeanSerializer(this, objectIdWriter);
    }

    @Override
    protected BeanSerializerBase withIgnorals(Set<String> toIgnore) {
        return new SuperSonicBeanSerializer(this, toIgnore);
    }

    // TODO: for now, lets bail out: in future may want to implement optimized variant
    @Override
    protected BeanSerializerBase asArraySerializer() {
        if (canCreateArraySerializer()) {
            return new BeanAsArraySerializer(this);
        }
        // already is one, so:
        return this;
    }

    @Override
    public BeanSerializerBase withFilterId(Object filterId) {
        return new SuperSonicBeanSerializer(this, _objectIdWriter, filterId);
    }

    /*
    /**********************************************************
    /* Actual serialization methods
    /**********************************************************
     */
    
    @Override
    public void serialize(Object bean, JsonGenerator gen, SerializerProvider provider)
            throws IOException
    {
        if (_objectIdWriter != null) {
            _serializeWithObjectId(bean, gen, provider, true);
            return;
        }
        if (_propertyFilterId != null) {
            gen.writeStartObject(bean);
            serializeFieldsFiltered(bean, gen, provider);
            gen.writeEndObject();
            return;
        }
        if (_filteredProps != null && provider.getActiveView() != null) {
            serializeWithView(bean, gen, provider, _filteredProps);
            return;
        }
        serializeNonFiltered(bean, gen, provider);
    }

    protected void serializeNonFiltered(Object bean, JsonGenerator gen, SerializerProvider provider)
        throws IOException
    {
        gen.writeStartObject(bean);
        final BeanPropertyWriter[] props = _props;
        BeanPropertyWriter prop = null;

        try {
            switch (_propCount) {
            default:
            //case 6:
                prop = _prop1;
                prop.serializeAsField(bean, gen, provider);
                // fall through
            case 5:
                prop = _prop2;
                prop.serializeAsField(bean, gen, provider);
            case 4:
                prop = _prop3;
                prop.serializeAsField(bean, gen, provider);
            case 3:
                prop = _prop4;
                prop.serializeAsField(bean, gen, provider);
            case 2:
                prop = _prop5;
                prop.serializeAsField(bean, gen, provider);
            case 1:
                prop = _prop6;
                prop.serializeAsField(bean, gen, provider);
            }
            prop = null;
            if (_anyGetterWriter != null) {
                _anyGetterWriter.getAndSerialize(bean, gen, provider);
            }
        } catch (Exception e) {
            String name = (prop == null) ? "[anySetter]" : prop.getName();
            wrapAndThrow(provider, e, bean, name);
        } catch (StackOverflowError e) {
            JsonMappingException mapE = new JsonMappingException(gen, "Infinite recursion (StackOverflowError)", e);
            String name = (prop == null) ? "[anySetter]" : prop.getName();
            mapE.prependPath(new JsonMappingException.Reference(bean, name));
            throw mapE;
        }
        gen.writeEndObject();
    }

    protected void serializeWithView(Object bean, JsonGenerator gen, SerializerProvider provider,
            BeanPropertyWriter[] props)
        throws IOException
    {
        gen.writeStartObject(bean);
        int i = 0;
        try {
            for (final int len = props.length; i < len; ++i) {
                BeanPropertyWriter prop = props[i];
                if (prop != null) { // can have nulls in filtered list
                    prop.serializeAsField(bean, gen, provider);
                }
            }
            if (_anyGetterWriter != null) {
                _anyGetterWriter.getAndSerialize(bean, gen, provider);
            }
        } catch (Exception e) {
            String name = (i == props.length) ? "[anySetter]" : props[i].getName();
            wrapAndThrow(provider, e, bean, name);
        } catch (StackOverflowError e) {
            // 04-Sep-2009, tatu: Dealing with this is tricky, since we don't have many
            //   stack frames to spare... just one or two; can't make many calls.

            // 10-Dec-2015, tatu: and due to above, avoid "from" method, call ctor directly:
            //JsonMappingException mapE = JsonMappingException.from(gen, "Infinite recursion (StackOverflowError)", e);
            JsonMappingException mapE = new JsonMappingException(gen, "Infinite recursion (StackOverflowError)", e);

             String name = (i == props.length) ? "[anySetter]" : props[i].getName();
            mapE.prependPath(new JsonMappingException.Reference(bean, name));
            throw mapE;
        }
        gen.writeEndObject();
    }
    
    /*
        protected void serializeFieldsFiltered(Object bean, JsonGenerator gen,
                SerializerProvider provider)
            throws IOException, JsonGenerationException
        {
            // note: almost verbatim copy of "serializeFields"; copied (instead of merged)
            // so that old method need not add check for existence of filter.
            final BeanPropertyWriter[] props;
            if (_filteredProps != null && provider.getActiveView() != null) {
                props = _filteredProps;
            } else {
                props = _props;
            }
            final PropertyFilter filter = findPropertyFilter(provider, _propertyFilterId, bean);
            // better also allow missing filter actually..
            if (filter == null) {
                serializeFields(bean, gen, provider);
                return;
            }
            int i = 0;
            try {
                for (final int len = props.length; i < len; ++i) {
                    BeanPropertyWriter prop = props[i];
                    if (prop != null) { // can have nulls in filtered list
                        filter.serializeAsField(bean, gen, provider, prop);
                    }
                }
                if (_anyGetterWriter != null) {
                    _anyGetterWriter.getAndFilter(bean, gen, provider, filter);
                }
            } catch (Exception e) {
                String name = (i == props.length) ? "[anySetter]" : props[i].getName();
                wrapAndThrow(provider, e, bean, name);
            } catch (StackOverflowError e) {
                // Minimize call depth since we are close to fail:
                //JsonMappingException mapE = JsonMappingException.from(gen, "Infinite recursion (StackOverflowError)", e);
                JsonMappingException mapE = new JsonMappingException(gen, "Infinite recursion (StackOverflowError)", e);
                String name = (i == props.length) ? "[anySetter]" : props[i].getName();
                mapE.prependPath(new JsonMappingException.Reference(bean, name));
                throw mapE;
            }
        }
*/
}
