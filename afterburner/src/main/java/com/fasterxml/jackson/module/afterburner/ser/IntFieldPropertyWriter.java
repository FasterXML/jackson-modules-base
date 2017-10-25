package com.fasterxml.jackson.module.afterburner.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

public final class IntFieldPropertyWriter
    extends OptimizedBeanPropertyWriter<IntFieldPropertyWriter>
{
    private static final long serialVersionUID = 1L;

    private final int _suppressableInt;
    private final boolean _suppressableSet;

    public IntFieldPropertyWriter(BeanPropertyWriter src, BeanPropertyAccessor acc, int index,
            JsonSerializer<Object> ser) {
        super(src, acc, index, ser);

        if (_suppressableValue instanceof Integer) {
            _suppressableInt = ((Integer)_suppressableValue).intValue();
            _suppressableSet = true;
        } else {
            _suppressableInt = 0;
            _suppressableSet = false;
        }
    }

    protected IntFieldPropertyWriter(IntFieldPropertyWriter base, PropertyName name) {
        super(base, name);
        _suppressableInt = base._suppressableInt;
        _suppressableSet = base._suppressableSet;
    }

    @Override
    protected BeanPropertyWriter _new(PropertyName newName) {
        return new IntFieldPropertyWriter(this, newName);
    }

    @Override
    public BeanPropertyWriter withSerializer(JsonSerializer<Object> ser) {
        return new IntFieldPropertyWriter(this, _propertyAccessor, _propertyIndex, ser);
    }
    
    @Override
    public IntFieldPropertyWriter withAccessor(BeanPropertyAccessor acc) {
        if (acc == null) throw new IllegalArgumentException();
        return new IntFieldPropertyWriter(this, acc, _propertyIndex, _serializer);
    }

    /*
    /**********************************************************
    /* Overrides
    /**********************************************************
     */

    @Override
    public final void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception
    {
        if (broken) {
            fallbackWriter.serializeAsField(bean, gen, prov);
            return;
        }
        int value;
        try {
            value = _propertyAccessor.intField(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, gen, prov, t, false);
            return;
        }
        if (!_suppressableSet || _suppressableInt != value) {
            gen.writeFieldName(_fastName);
            gen.writeNumber(value);
        }
    }

    @Override
    public final void serializeAsElement(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception
    {
        if (broken) {
            fallbackWriter.serializeAsElement(bean, gen, prov);
            return;
        }
        int value;
        try {
            value = _propertyAccessor.intField(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, gen, prov, t, true);
            return;
        }
        if (!_suppressableSet || (_suppressableInt != value)) {
            gen.writeNumber(value);
        } else { // important: MUST output a placeholder
            serializeAsPlaceholder(bean, gen, prov);
        }
    }
}
