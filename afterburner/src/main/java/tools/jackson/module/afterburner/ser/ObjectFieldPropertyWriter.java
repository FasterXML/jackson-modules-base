package tools.jackson.module.afterburner.ser;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializerProvider;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.impl.PropertySerializerMap;

public final class ObjectFieldPropertyWriter
    extends OptimizedBeanPropertyWriter<ObjectFieldPropertyWriter>
{
    private static final long serialVersionUID = 1L;

    public ObjectFieldPropertyWriter(BeanPropertyWriter src, BeanPropertyAccessor acc, int index,
            ValueSerializer<Object> ser) {
        super(src, acc, index, ser);
    }

    protected ObjectFieldPropertyWriter(ObjectFieldPropertyWriter base, PropertyName name) {
        super(base, name);
    }

    @Override
    protected BeanPropertyWriter _new(PropertyName newName) {
        return new ObjectFieldPropertyWriter(this, newName);
    }

    @Override
    public BeanPropertyWriter withSerializer(ValueSerializer<Object> ser) {
        return new ObjectFieldPropertyWriter(this, _propertyAccessor, _propertyIndex, ser);
    }

    @Override
    public ObjectFieldPropertyWriter withAccessor(BeanPropertyAccessor acc) {
        if (acc == null) throw new IllegalArgumentException();
        return new ObjectFieldPropertyWriter(this, acc, _propertyIndex, _serializer);
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
            value = _propertyAccessor.objectField(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, g, prov, t, false);
            return;
        }
        // Null (etc) handling; copied from super-class impl
        if (value == null) {
            // 20-Jun-2022, tatu: Defer checking of null, see [databind#3481]
            if ((_suppressableValue != null)
                    && prov.includeFilterSuppressNulls(_suppressableValue)) {
                return;
            }
            if (_nullSerializer != null) {
                g.writeName(_fastName);
                _nullSerializer.serialize(null, g, prov);
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
            value = _propertyAccessor.objectField(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, g, prov, t, true);
            return;
        }
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
