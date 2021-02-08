package com.fasterxml.jackson.module.afterburner.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ValueSerializer;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

public final class BooleanMethodPropertyWriter
    extends OptimizedBeanPropertyWriter<BooleanMethodPropertyWriter>
{
    private static final long serialVersionUID = 1L;

    private final boolean _suppressableSet;
    private final boolean _suppressableBoolean;

    public BooleanMethodPropertyWriter(BeanPropertyWriter src, BeanPropertyAccessor acc, int index,
            ValueSerializer<Object> ser) {
        super(src, acc, index, ser);

        if (_suppressableValue instanceof Boolean) {
            _suppressableBoolean = ((Boolean)_suppressableValue).booleanValue();
            _suppressableSet = true;
        } else {
            _suppressableBoolean = false;
            _suppressableSet = false;
        }
    }

    protected BooleanMethodPropertyWriter(BooleanMethodPropertyWriter base, PropertyName name) {
        super(base, name);
        _suppressableSet = base._suppressableSet;
        _suppressableBoolean = base._suppressableBoolean;
    }

    @Override
    protected BeanPropertyWriter _new(PropertyName newName) {
        return new BooleanMethodPropertyWriter(this, newName);
    }

    @Override
    public BeanPropertyWriter withSerializer(ValueSerializer<Object> ser) {
        return new BooleanMethodPropertyWriter(this, _propertyAccessor, _propertyIndex, ser);
    }
    
    @Override
    public BooleanMethodPropertyWriter withAccessor(BeanPropertyAccessor acc) {
        if (acc == null) throw new IllegalArgumentException();
        return new BooleanMethodPropertyWriter(this, acc, _propertyIndex, _serializer);
    }

    /*
    /**********************************************************************
    /* Overrides
    /**********************************************************************
     */

    @Override
    public final void serializeAsProperty(Object bean, JsonGenerator g, SerializerProvider prov)
        throws Exception
    {
        if (broken) {
            fallbackWriter.serializeAsProperty(bean, g, prov);
            return;
        }
        boolean value;
        try {
            value = _propertyAccessor.booleanGetter(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, g, prov, t, false);
            return;
        }
        if (!_suppressableSet || _suppressableBoolean != value) {
            g.writeName(_fastName);
            g.writeBoolean(value);
        }
    }

    @Override
    public final void serializeAsElement(Object bean, JsonGenerator g, SerializerProvider prov)
        throws Exception
    {
        if (broken) {
            fallbackWriter.serializeAsElement(bean, g, prov);
            return;
        }            
        boolean value;
        try {
            value = _propertyAccessor.booleanGetter(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, g, prov, t, true);
            return;
        }
        if (!_suppressableSet || _suppressableBoolean != value) {
            g.writeBoolean(value);
        } else { // important: MUST output a placeholder
            serializeAsOmittedElement(bean, g, prov);
        }
    }
}
