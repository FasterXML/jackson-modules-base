package com.fasterxml.jackson.module.afterburner.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

public final class StringFieldPropertyWriter
    extends OptimizedBeanPropertyWriter<StringFieldPropertyWriter>
{
    private static final long serialVersionUID = 1L;

    public StringFieldPropertyWriter(BeanPropertyWriter src, BeanPropertyAccessor acc, int index,
            JsonSerializer<Object> ser) {
        super(src, acc, index, ser);
    }

    @Override
    public BeanPropertyWriter withSerializer(JsonSerializer<Object> ser) {
        return new StringFieldPropertyWriter(this, _propertyAccessor, _propertyIndex, ser);
    }

    @Override
    public StringFieldPropertyWriter withAccessor(BeanPropertyAccessor acc) {
        if (acc == null) throw new IllegalArgumentException();
        return new StringFieldPropertyWriter(this, acc, _propertyIndex, _serializer);
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
        String value;
        try {
            value = _propertyAccessor.stringField(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, gen, prov, t, false);
            return;
        }
        // Null (etc) handling; copied from super-class impl
        if (value == null) {
            if (!_suppressNulls) {
                gen.writeFieldName(_fastName);
                prov.defaultSerializeNull(gen);
            }
            return;
        }
        if (_suppressableValue != null) {
            if (MARKER_FOR_EMPTY == _suppressableValue) {
                if (value.length() == 0) {
                    return;
                }
            } else if (_suppressableValue.equals(value)) {
                return;
            }
        }
        gen.writeFieldName(_fastName);
        gen.writeString(value);
    }

    @Override
    public final void serializeAsElement(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception
    {
        if (broken) {
            fallbackWriter.serializeAsElement(bean, gen, prov);
            return;
        }
        String value;
        try {
            value = _propertyAccessor.stringField(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, gen, prov, t, true);
            return;
        }
        if (_suppressableValue != null) {
            if (MARKER_FOR_EMPTY == _suppressableValue) {
                if (value.length() == 0) {
                    serializeAsPlaceholder(bean, gen, prov);
                    return;
                }
            } else if (_suppressableValue.equals(value)) {
                serializeAsPlaceholder(bean, gen, prov);
                return;
            }
        }
        gen.writeString(value);
    }
}
