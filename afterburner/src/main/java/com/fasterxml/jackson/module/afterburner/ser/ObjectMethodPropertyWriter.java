package com.fasterxml.jackson.module.afterburner.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.PropertySerializerMap;

public final class ObjectMethodPropertyWriter
    extends OptimizedBeanPropertyWriter<ObjectMethodPropertyWriter>
{
    private static final long serialVersionUID = 1L;

    public ObjectMethodPropertyWriter(BeanPropertyWriter src, BeanPropertyAccessor acc, int index,
            JsonSerializer<Object> ser) {
        super(src, acc, index, ser);
    }

    protected ObjectMethodPropertyWriter(ObjectMethodPropertyWriter base, PropertyName name) {
        super(base, name);
    }

    @Override
    protected BeanPropertyWriter _new(PropertyName newName) {
        return new ObjectMethodPropertyWriter(this, newName);
    }

    @Override
    public BeanPropertyWriter withSerializer(JsonSerializer<Object> ser) {
        return new ObjectMethodPropertyWriter(this, _propertyAccessor, _propertyIndex, ser);
    }

    @Override
    public ObjectMethodPropertyWriter withAccessor(BeanPropertyAccessor acc) {
        if (acc == null) throw new IllegalArgumentException();
        return new ObjectMethodPropertyWriter(this, acc, _propertyIndex, _serializer);
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
        Object value;
        try {
            value = _propertyAccessor.objectGetter(bean, _propertyIndex);
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
        JsonSerializer<Object> ser = _serializer;
        if (ser == null) {
            Class<?> cls = value.getClass();
            PropertySerializerMap map = _dynamicSerializers;
            ser = map.serializerFor(cls);
            if (ser == null) {
                ser = _findAndAddDynamic(map, cls, prov);
            }
        }
        if (_suppressableValue != null) {
            if (MARKER_FOR_EMPTY == _suppressableValue) {
                if (ser.isEmpty(prov, value)) {
                    return;
                }
            } else if (_suppressableValue.equals(value)) {
                return;
            }
        }
        if (value == bean) {
            // three choices: exception; handled by call; or pass-through
            if (_handleSelfReference(bean, gen, prov, ser)) {
                return;
            }
        }
        gen.writeFieldName(_fastName);
        if (_typeSerializer == null) {
            ser.serialize(value, gen, prov);
        } else {
            ser.serializeWithType(value, gen, prov, _typeSerializer);
        }
    }

    @Override
    public final void serializeAsElement(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception
    {
        if (broken) {
            fallbackWriter.serializeAsElement(bean, gen, prov);
            return;
        }
        Object value;
        try {
            value = _propertyAccessor.objectGetter(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, gen, prov, t, true);
            return;
        }
        // Null (etc) handling; copied from super-class impl
        if (value == null) {
            if (_nullSerializer != null) {
                _nullSerializer.serialize(null, gen, prov);
            } else if (_suppressNulls) {
                serializeAsPlaceholder(bean, gen, prov);
            } else {
                prov.defaultSerializeNullValue(gen);
            }
            return;
        }
        JsonSerializer<Object> ser = _serializer;
        if (ser == null) {
            Class<?> cls = value.getClass();
            PropertySerializerMap map = _dynamicSerializers;
            ser = map.serializerFor(cls);
            if (ser == null) {
                ser = _findAndAddDynamic(map, cls, prov);
            }
        }
        if (_suppressableValue != null) {
            if (MARKER_FOR_EMPTY == _suppressableValue) {
                if (ser.isEmpty(prov, value)) {
                    serializeAsPlaceholder(bean, gen, prov);
                    return;
                }
            } else if (_suppressableValue.equals(value)) {
                serializeAsPlaceholder(bean, gen, prov);
                return;
            }
        }
        if (value == bean) {
            // three choices: exception; handled by call; or pass-through
            if (_handleSelfReference(bean, gen, prov, ser)) {
                return;
            }
        }
        if (_typeSerializer == null) {
            ser.serialize(value, gen, prov);
        } else {
            ser.serializeWithType(value, gen, prov, _typeSerializer);
        }
    }
}
