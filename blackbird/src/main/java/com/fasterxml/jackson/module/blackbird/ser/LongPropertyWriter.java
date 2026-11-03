package com.fasterxml.jackson.module.blackbird.ser;

import java.util.function.ToLongFunction;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

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

    public LongPropertyWriter(BeanPropertyWriter src, ToLongFunction<Object> acc, JsonSerializer<Object> ser) {
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
    public BeanPropertyWriter withSerializer(JsonSerializer<Object> ser) {
        return new LongPropertyWriter(this, _acc, ser);
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
        long value;
        try {
            value = _acc.applyAsLong(bean);
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
        long value;
        try {
            value = _acc.applyAsLong(bean);
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

    private boolean _isSuppressed(long value) {
        if (_suppressableSet) {
            return _suppressableLong == value;
        }
        return _suppressableFilter && _suppressableValue.equals(value);
    }
}
