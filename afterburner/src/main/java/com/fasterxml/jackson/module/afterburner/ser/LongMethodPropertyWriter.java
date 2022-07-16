package com.fasterxml.jackson.module.afterburner.ser;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializerProvider;
import tools.jackson.databind.ser.BeanPropertyWriter;

public final class LongMethodPropertyWriter
    extends OptimizedBeanPropertyWriter<LongMethodPropertyWriter>
{
    private static final long serialVersionUID = 1L;

    private final long _suppressableLong;
    private final boolean _suppressableSet;

    public LongMethodPropertyWriter(BeanPropertyWriter src, BeanPropertyAccessor acc, int index,
            ValueSerializer<Object> ser) {
        super(src, acc, index, ser);

        if (_suppressableValue instanceof Long) {
            _suppressableLong = (Long)_suppressableValue;
            _suppressableSet = true;
        } else {
            _suppressableLong = 0L;
            _suppressableSet = false;
        }
    }

    protected LongMethodPropertyWriter(LongMethodPropertyWriter base, PropertyName name) {
        super(base, name);
        _suppressableSet = base._suppressableSet;
        _suppressableLong = base._suppressableLong;
    }

    @Override
    protected BeanPropertyWriter _new(PropertyName newName) {
        return new LongMethodPropertyWriter(this, newName);
    }

    @Override
    public BeanPropertyWriter withSerializer(ValueSerializer<Object> ser) {
        return new LongMethodPropertyWriter(this, _propertyAccessor, _propertyIndex, ser);
    }

    @Override
    public LongMethodPropertyWriter withAccessor(BeanPropertyAccessor acc) {
        if (acc == null) throw new IllegalArgumentException();
        return new LongMethodPropertyWriter(this, acc, _propertyIndex, _serializer);
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
        long value;
        try {
            value = _propertyAccessor.longGetter(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, g, prov, t, false);
            return;
        }
        if (!_suppressableSet || _suppressableLong != value) {
            g.writeName(_fastName);
            g.writeNumber(value);
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
        long value;
        try {
            value = _propertyAccessor.longGetter(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, g, prov, t, true);
            return;
        }
        if (!_suppressableSet || _suppressableLong != value) {
            g.writeNumber(value);
        } else { // important: MUST output a placeholder
            serializeAsOmittedElement(bean, g, prov);
        }
    }
}
