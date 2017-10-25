package com.fasterxml.jackson.module.afterburner.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

public final class LongMethodPropertyWriter
    extends OptimizedBeanPropertyWriter<LongMethodPropertyWriter>
{
    private static final long serialVersionUID = 1L;

    private final long _suppressableLong;
    private final boolean _suppressableSet;

    public LongMethodPropertyWriter(BeanPropertyWriter src, BeanPropertyAccessor acc, int index,
            JsonSerializer<Object> ser) {
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
    public BeanPropertyWriter withSerializer(JsonSerializer<Object> ser) {
        return new LongMethodPropertyWriter(this, _propertyAccessor, _propertyIndex, ser);
    }

    @Override
    public LongMethodPropertyWriter withAccessor(BeanPropertyAccessor acc) {
        if (acc == null) throw new IllegalArgumentException();
        return new LongMethodPropertyWriter(this, acc, _propertyIndex, _serializer);
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
        long value;
        try {
            value = _propertyAccessor.longGetter(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, gen, prov, t, false);
            return;
        }
        if (!_suppressableSet || _suppressableLong != value) {
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
        long value;
        try {
            value = _propertyAccessor.longGetter(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, gen, prov, t, true);
            return;
        }
        if (!_suppressableSet || _suppressableLong != value) {
            gen.writeNumber(value);
        } else { // important: MUST output a placeholder
            serializeAsPlaceholder(bean, gen, prov);
        }
    }
}
