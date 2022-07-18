package tools.jackson.module.afterburner.ser;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializerProvider;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.impl.PropertySerializerMap;

public final class ObjectMethodPropertyWriter
    extends OptimizedBeanPropertyWriter<ObjectMethodPropertyWriter>
{
    private static final long serialVersionUID = 1L;

    public ObjectMethodPropertyWriter(BeanPropertyWriter src, BeanPropertyAccessor acc, int index,
            ValueSerializer<Object> ser) {
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
    public BeanPropertyWriter withSerializer(ValueSerializer<Object> ser) {
        return new ObjectMethodPropertyWriter(this, _propertyAccessor, _propertyIndex, ser);
    }

    @Override
    public ObjectMethodPropertyWriter withAccessor(BeanPropertyAccessor acc) {
        if (acc == null) throw new IllegalArgumentException();
        return new ObjectMethodPropertyWriter(this, acc, _propertyIndex, _serializer);
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
        Object value;
        try {
            value = _propertyAccessor.objectGetter(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, g, prov, t, false);
            return;
        }
        // Null (etc) handling; copied from super-class impl
        if (value == null) {
            if (_nullSerializer != null) {
                g.writeName(_fastName);
                _nullSerializer.serialize(null, g, prov);
            } else if (!_suppressNulls) {
                g.writeName(_fastName);
                prov.defaultSerializeNullValue(g);
            }
            return;
        }
        ValueSerializer<Object> ser = _serializer;
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
            if (_handleSelfReference(bean, g, prov, ser)) {
                return;
            }
        }
        g.writeName(_fastName);
        if (_typeSerializer == null) {
            ser.serialize(value, g, prov);
        } else {
            ser.serializeWithType(value, g, prov, _typeSerializer);
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
        Object value;
        try {
            value = _propertyAccessor.objectGetter(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, g, prov, t, true);
            return;
        }
        // Null (etc) handling; copied from super-class impl
        if (value == null) {
            if (_nullSerializer != null) {
                _nullSerializer.serialize(null, g, prov);
            } else if (_suppressNulls) {
                serializeAsOmittedElement(bean, g, prov);
            } else {
                prov.defaultSerializeNullValue(g);
            }
            return;
        }
        ValueSerializer<Object> ser = _serializer;
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
                    serializeAsOmittedElement(bean, g, prov);
                    return;
                }
            } else if (_suppressableValue.equals(value)) {
                serializeAsOmittedElement(bean, g, prov);
                return;
            }
        }
        if (value == bean) {
            // three choices: exception; handled by call; or pass-through
            if (_handleSelfReference(bean, g, prov, ser)) {
                return;
            }
        }
        if (_typeSerializer == null) {
            ser.serialize(value, g, prov);
        } else {
            ser.serializeWithType(value, g, prov, _typeSerializer);
        }
    }
}
