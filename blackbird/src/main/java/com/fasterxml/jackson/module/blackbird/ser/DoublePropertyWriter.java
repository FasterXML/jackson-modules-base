package com.fasterxml.jackson.module.blackbird.ser;

import java.util.function.ToDoubleFunction;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

final class DoublePropertyWriter
    extends OptimizedBeanPropertyWriter<DoublePropertyWriter>
{
    private static final long serialVersionUID = 1L;

    // `BeanPropertyWriter` suppresses a value that `equals` the boxed suppressable
    // one, so compare as `Double.equals` does: `-0.0` isn't `0.0`, and `NaN` is `NaN`
    private final long _suppressableBits;
    private final boolean _suppressableSet;

    // A suppressable value that isn't a `Double`, like a custom inclusion filter,
    // decides through its own `equals`, which is given the boxed value as
    // `BeanPropertyWriter` does. (`NON_EMPTY` never suppresses a double: the
    // default serializer's `isEmpty` only checks for null.)
    private final boolean _suppressableFilter;

    private final ToDoubleFunction<Object> _acc;

    public DoublePropertyWriter(BeanPropertyWriter src, ToDoubleFunction<Object> acc, JsonSerializer<Object> ser) {
        super(src, ser);
        _acc = acc;

        if (_suppressableValue instanceof Double) {
            _suppressableBits = Double.doubleToLongBits((Double) _suppressableValue);
            _suppressableSet = true;
            _suppressableFilter = false;
        } else {
            _suppressableBits = 0L;
            _suppressableSet = false;
            _suppressableFilter = (_suppressableValue != null) && (_suppressableValue != MARKER_FOR_EMPTY);
        }
    }

    protected DoublePropertyWriter(DoublePropertyWriter base, PropertyName name) {
        super(base, name);
        _suppressableSet = base._suppressableSet;
        _suppressableBits = base._suppressableBits;
        _suppressableFilter = base._suppressableFilter;
        _acc = base._acc;
    }

    @Override
    protected BeanPropertyWriter _new(PropertyName newName) {
        return new DoublePropertyWriter(this, newName);
    }

    @Override
    public BeanPropertyWriter withSerializer(JsonSerializer<Object> ser) {
        return new DoublePropertyWriter(this, _acc, ser);
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
        double value;
        try {
            value = _acc.applyAsDouble(bean);
        } catch (Throwable t) {
            _handleProblem(bean, gen, prov, t, false);
            return;
        }
        if (!_isSuppressed(value)) {
            gen.writeFieldName(_fastName);
            gen.writeNumber(value);
        }
    }

    @Override
    public final void serializeAsElement(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception
    {
        if (broken) {
            fallbackWriter.serializeAsElement(bean, gen, prov);
            return;
        }
        double value;
        try {
            value = _acc.applyAsDouble(bean);
        } catch (Throwable t) {
            _handleProblem(bean, gen, prov, t, true);
            return;
        }
        if (!_isSuppressed(value)) {
            gen.writeNumber(value);
        } else { // important: MUST output a placeholder
            serializeAsPlaceholder(bean, gen, prov);
        }
    }

    private boolean _isSuppressed(double value) {
        if (_suppressableSet) {
            return _suppressableBits == Double.doubleToLongBits(value);
        }
        return _suppressableFilter && _suppressableValue.equals(value);
    }
}
