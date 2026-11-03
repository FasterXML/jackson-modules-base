package tools.jackson.module.blackbird.ser;

import java.util.function.ToLongFunction;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.BeanPropertyWriter;

final class LongPropertyWriter
    extends OptimizedBeanPropertyWriter<LongPropertyWriter>
{
    private static final long serialVersionUID = 1L;

    private final long _suppressableLong;
    private final boolean _suppressableSet;

    // A suppressable value that isn't of the property type, like a custom inclusion
    // filter, decides through its own `equals`, which is given the boxed value as
    // `BeanPropertyWriter` does
    private final boolean _suppressableFilter;

    private final ToLongFunction<Object> _acc;

    public LongPropertyWriter(BeanPropertyWriter src, ToLongFunction<Object> acc, ValueSerializer<Object> ser) {
        super(src, ser);
        _acc = acc;

        if (_suppressableValue instanceof Long) {
            _suppressableLong = (Long)_suppressableValue;
            _suppressableSet = true;
            _suppressableFilter = false;
        } else {
            _suppressableLong = 0L;
            _suppressableSet = false;
            _suppressableFilter = (_suppressableValue != null) && (_suppressableValue != MARKER_FOR_EMPTY);
        }
    }

    protected LongPropertyWriter(LongPropertyWriter base, PropertyName name) {
        super(base, name);
        _suppressableSet = base._suppressableSet;
        _suppressableFilter = base._suppressableFilter;
        _suppressableLong = base._suppressableLong;
        _acc = base._acc;
    }

    @Override
    protected BeanPropertyWriter _new(PropertyName newName) {
        return new LongPropertyWriter(this, newName);
    }

    @Override
    public BeanPropertyWriter withSerializer(ValueSerializer<Object> ser) {
        return new LongPropertyWriter(this, _acc, ser);
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
        long value;
        try {
            value = _acc.applyAsLong(bean);
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
        long value;
        try {
            value = _acc.applyAsLong(bean);
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

    private boolean _isSuppressed(long value) {
        if (_suppressableSet) {
            return _suppressableLong == value;
        }
        return _suppressableFilter && _suppressableValue.equals(value);
    }
}
