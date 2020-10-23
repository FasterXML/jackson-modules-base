package com.fasterxml.jackson.module.blackbird.ser;

import java.util.function.Function;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.PropertySerializerMap;

final class ObjectPropertyWriter
    extends OptimizedBeanPropertyWriter<ObjectPropertyWriter>
{
    private static final long serialVersionUID = 1L;
    private final Function<Object, Object> _acc;

    public ObjectPropertyWriter(BeanPropertyWriter src, Function<Object, Object> acc, JsonSerializer<Object> ser) {
        super(src, ser);
        _acc = acc;
    }

    protected ObjectPropertyWriter(ObjectPropertyWriter base, PropertyName name) {
        super(base, name);
        _acc = base._acc;
    }

    @Override
    protected BeanPropertyWriter _new(PropertyName newName) {
        return new ObjectPropertyWriter(this, newName);
    }

    @Override
    public BeanPropertyWriter withSerializer(JsonSerializer<Object> ser) {
        return new ObjectPropertyWriter(this, _acc, ser);
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
            value = _acc.apply(bean);
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
                prov.defaultSerializeNull(gen);
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
            value = _acc.apply(bean);
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
                prov.defaultSerializeNull(gen);
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
