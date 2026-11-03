package com.fasterxml.jackson.module.blackbird.ser;

import java.util.function.ToIntFunction;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

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

    public IntPropertyWriter(BeanPropertyWriter src, ToIntFunction<Object> acc, JsonSerializer<Object> ser) {
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
    public BeanPropertyWriter withSerializer(JsonSerializer<Object> ser) {
        return new IntPropertyWriter(this, _acc, ser);
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
        int value;
        try {
            value = _acc.applyAsInt(bean);
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
        int value;
        try {
            value = _acc.applyAsInt(bean);
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

    private boolean _isSuppressed(int value) {
        if (_suppressableIntSet) {
            return _suppressableInt == value;
        }
        return _suppressableFilter && _suppressableValue.equals(value);
    }
}
