package tools.jackson.module.afterburner.ser;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.BeanPropertyWriter;

public final class IntMethodPropertyWriter
    extends OptimizedBeanPropertyWriter<IntMethodPropertyWriter>
{
    private static final long serialVersionUID = 1L;

    private final int _suppressableInt;
    private final boolean _suppressableIntSet;

    public IntMethodPropertyWriter(BeanPropertyWriter src, BeanPropertyAccessor acc, int index,
            ValueSerializer<Object> ser) {
        super(src, acc, index, ser);

        if (_suppressableValue instanceof Integer) {
            _suppressableInt = (Integer)_suppressableValue;
            _suppressableIntSet = true;
        } else {
            _suppressableInt = 0;
            _suppressableIntSet = false;
        }
    }

    protected IntMethodPropertyWriter(IntMethodPropertyWriter base, PropertyName name) {
        super(base, name);
        _suppressableInt = base._suppressableInt;
        _suppressableIntSet = base._suppressableIntSet;
    }

    @Override
    protected BeanPropertyWriter _new(PropertyName newName) {
        return new IntMethodPropertyWriter(this, newName);
    }

    @Override
    public BeanPropertyWriter withSerializer(ValueSerializer<Object> ser) {
        return new IntMethodPropertyWriter(this, _propertyAccessor, _propertyIndex, ser);
    }
    
    @Override
    public IntMethodPropertyWriter withAccessor(BeanPropertyAccessor acc) {
        if (acc == null) throw new IllegalArgumentException();
        return new IntMethodPropertyWriter(this, acc, _propertyIndex, _serializer);
    }

    /*
    /**********************************************************************
    /* Overrides
    /**********************************************************************
     */

    @Override
    public final void serializeAsProperty(Object bean, JsonGenerator g, SerializationContext ctxt) throws Exception
    {
        if (broken) {
            fallbackWriter.serializeAsProperty(bean, g, ctxt);
            return;
        }
        int value;
        try {
            value = _propertyAccessor.intGetter(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, g, ctxt, t, false);
            return;
        }
        if (!_suppressableIntSet || _suppressableInt != value) {
            g.writeName(_fastName);
            g.writeNumber(value);
        }
    }

    @Override
    public final void serializeAsElement(Object bean, JsonGenerator g, SerializationContext ctxt) throws Exception
    {
        if (broken) {
            fallbackWriter.serializeAsElement(bean, g, ctxt);
            return;
        }
        int value;
        try {
            value = _propertyAccessor.intGetter(bean, _propertyIndex);
        } catch (Throwable t) {
            _handleProblem(bean, g, ctxt, t, true);
            return;
        }
        if (!_suppressableIntSet || _suppressableInt != value) {
            g.writeNumber(value);
        } else { // important: MUST output a placeholder
            serializeAsOmittedElement(bean, g, ctxt);
        }
    }
}
