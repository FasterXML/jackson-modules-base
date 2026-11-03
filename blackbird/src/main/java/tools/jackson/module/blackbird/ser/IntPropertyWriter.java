package tools.jackson.module.blackbird.ser;

import java.util.function.ToIntFunction;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.BeanPropertyWriter;

final class IntPropertyWriter
    extends OptimizedBeanPropertyWriter<IntPropertyWriter>
{
    private static final long serialVersionUID = 1L;

    private final int _suppressableInt;
    private final boolean _suppressableIntSet;

    // A suppressable value that isn't of the property type, like a custom inclusion
    // filter, decides through its own `equals`, which is given the boxed value as
    // `BeanPropertyWriter` does
    private final boolean _suppressableFilter;

    private final ToIntFunction<Object> _acc;

    public IntPropertyWriter(BeanPropertyWriter src, ToIntFunction<Object> acc, ValueSerializer<Object> ser) {
        super(src, ser);
        _acc = acc;

        if (_suppressableValue instanceof Integer) {
            _suppressableInt = (Integer)_suppressableValue;
            _suppressableIntSet = true;
            _suppressableFilter = false;
        } else {
            _suppressableInt = 0;
            _suppressableIntSet = false;
            _suppressableFilter = (_suppressableValue != null) && (_suppressableValue != MARKER_FOR_EMPTY);
        }
    }

    protected IntPropertyWriter(IntPropertyWriter base, PropertyName name) {
        super(base, name);
        _suppressableInt = base._suppressableInt;
        _suppressableIntSet = base._suppressableIntSet;
        _suppressableFilter = base._suppressableFilter;
        _acc = base._acc;
    }

    @Override
    protected BeanPropertyWriter _new(PropertyName newName) {
        return new IntPropertyWriter(this, newName);
    }

    @Override
    public BeanPropertyWriter withSerializer(ValueSerializer<Object> ser) {
        return new IntPropertyWriter(this, _acc, ser);
    }

    /*
    /**********************************************************************
    /* Overrides
    /**********************************************************************
     */

    @Override
    public final void serializeAsProperty(Object bean, JsonGenerator g, SerializationContext ctxt)
        throws Exception
    {
        if (broken) {
            fallbackWriter.serializeAsProperty(bean, g, ctxt);
            return;
        }
        int value;
        try {
            value = _acc.applyAsInt(bean);
        } catch (Throwable t) {
            _handleProblem(bean, g, ctxt, t, false);
            return;
        }
        if (!_isSuppressed(value)) {
            g.writeName(_fastName);
            g.writeNumber(value);
        }
    }

    @Override
    public final void serializeAsElement(Object bean, JsonGenerator g, SerializationContext ctxt)
        throws Exception
    {
        if (broken) {
            fallbackWriter.serializeAsElement(bean, g, ctxt);
            return;
        }
        int value;
        try {
            value = _acc.applyAsInt(bean);
        } catch (Throwable t) {
            _handleProblem(bean, g, ctxt, t, true);
            return;
        }
        if (!_isSuppressed(value)) {
            g.writeNumber(value);
        } else { // important: MUST output a placeholder
            serializeAsOmittedElement(bean, g, ctxt);
        }
    }

    private boolean _isSuppressed(int value) {
        if (_suppressableIntSet) {
            return _suppressableInt == value;
        }
        return _suppressableFilter && _suppressableValue.equals(value);
    }
}
