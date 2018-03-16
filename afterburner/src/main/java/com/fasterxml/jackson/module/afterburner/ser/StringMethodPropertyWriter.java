package com.fasterxml.jackson.module.afterburner.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

public final class StringMethodPropertyWriter 
    extends OptimizedBeanPropertyWriter<StringMethodPropertyWriter>
{
    private static final long serialVersionUID = 1L;

    public StringMethodPropertyWriter(BeanPropertyWriter src, BeanPropertyAccessor acc, int index,
            JsonSerializer<Object> ser) {
        super(src, acc, index, ser);
    }

    protected StringMethodPropertyWriter(StringMethodPropertyWriter base, PropertyName name) {
        super(base, name);
    }

    @Override
    protected BeanPropertyWriter _new(PropertyName newName) {
        return new StringMethodPropertyWriter(this, newName);
    }

    @Override
    public BeanPropertyWriter withSerializer(JsonSerializer<Object> ser) {
        return new StringMethodPropertyWriter(this, _propertyAccessor, _propertyIndex, ser);
    }
    
    @Override
    public StringMethodPropertyWriter withAccessor(BeanPropertyAccessor acc) {
        if (acc == null) throw new IllegalArgumentException();
        return new StringMethodPropertyWriter(this, acc, _propertyIndex, _serializer);
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
            value = _propertyAccessor.stringGetter(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, gen, prov, t, false);
            return;
        }
        // Null (etc) handling; copied from super-class impl
        if (value == null) {
            if (_nullSerializer != null) {
                gen.writeFieldName(_fastName);
                _nullSerializer.serialize(null, gen, prov);
            } else if (!_suppressNulls) {
                gen.writeFieldName(_fastName);
                prov.defaultSerializeNullValue(gen);
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
            value = _propertyAccessor.stringGetter(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, gen, prov, t, true);
            return;
        }
        // Null (etc) handling; copied from super-class impl
        if (value == null) {
            if (_suppressNulls) {
                serializeAsPlaceholder(bean, gen, prov);
            } else {
                prov.defaultSerializeNullValue(gen);
            }
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
