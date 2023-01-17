package tools.jackson.module.afterburner.ser;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializerProvider;
import tools.jackson.databind.ser.BeanPropertyWriter;

public final class StringMethodPropertyWriter 
    extends OptimizedBeanPropertyWriter<StringMethodPropertyWriter>
{
    private static final long serialVersionUID = 1L;

    public StringMethodPropertyWriter(BeanPropertyWriter src, BeanPropertyAccessor acc, int index,
            ValueSerializer<Object> ser) {
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
    public BeanPropertyWriter withSerializer(ValueSerializer<Object> ser) {
        return new StringMethodPropertyWriter(this, _propertyAccessor, _propertyIndex, ser);
    }
    
    @Override
    public StringMethodPropertyWriter withAccessor(BeanPropertyAccessor acc) {
        if (acc == null) throw new IllegalArgumentException();
        return new StringMethodPropertyWriter(this, acc, _propertyIndex, _serializer);
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
        String value;
        try {
            value = _propertyAccessor.stringGetter(bean, _propertyIndex);
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
        if (_suppressableValue != null) {
            if (MARKER_FOR_EMPTY == _suppressableValue) {
                if (value.length() == 0) {
                    return;
                }
            } else if (_suppressableValue.equals(value)) {
                return;
            }
        }
        g.writeName(_fastName);
        g.writeString(value);
    }

    @Override
    public final void serializeAsElement(Object bean, JsonGenerator g, SerializerProvider prov)
        throws Exception
    {
        if (broken) {
            fallbackWriter.serializeAsElement(bean, g, prov);
            return;
        }

        String value;
        try {
            value = _propertyAccessor.stringGetter(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, g, prov, t, true);
            return;
        }
        // Null (etc) handling; copied from super-class impl
        if (value == null) {
            if (_suppressNulls) {
                serializeAsOmittedElement(bean, g, prov);
            } else {
                prov.defaultSerializeNullValue(g);
            }
            return;
        }
        if (_suppressableValue != null) {
            if (MARKER_FOR_EMPTY == _suppressableValue) {
                if (value.length() == 0) {
                    serializeAsOmittedElement(bean, g, prov);
                    return;
                }
            } else if (_suppressableValue.equals(value)) {
                serializeAsOmittedElement(bean, g, prov);
                return;
            }
        }
        g.writeString(value);
    }
}
