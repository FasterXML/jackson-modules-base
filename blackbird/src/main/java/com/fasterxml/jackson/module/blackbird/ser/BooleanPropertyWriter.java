package com.fasterxml.jackson.module.blackbird.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ValueSerializer;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

final class BooleanPropertyWriter
    extends OptimizedBeanPropertyWriter<BooleanPropertyWriter>
{
    private static final long serialVersionUID = 1L;

    private final boolean _suppressableSet;
    private final boolean _suppressableBoolean;

    private final ToBooleanFunction _acc;

    public BooleanPropertyWriter(BeanPropertyWriter src, ToBooleanFunction acc, ValueSerializer<Object> ser) {
        super(src, ser);
        _acc = acc;

        if (_suppressableValue instanceof Boolean) {
            _suppressableBoolean = ((Boolean)_suppressableValue).booleanValue();
            _suppressableSet = true;
        } else {
            _suppressableBoolean = false;
            _suppressableSet = false;
        }
    }

    protected BooleanPropertyWriter(BooleanPropertyWriter base, PropertyName name) {
        super(base, name);
        _suppressableSet = base._suppressableSet;
        _suppressableBoolean = base._suppressableBoolean;
        _acc = base._acc;
    }

    @Override
    protected BeanPropertyWriter _new(PropertyName newName) {
        return new BooleanPropertyWriter(this, newName);
    }

    @Override
    public BeanPropertyWriter withSerializer(ValueSerializer<Object> ser) {
        return new BooleanPropertyWriter(this, _acc, ser);
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
        boolean value;
        try {
            value = _acc.applyAsBoolean(bean);
        } catch (Throwable t) {
            _handleProblem(bean, g, prov, t, false);
            return;
        }
        if (!_suppressableSet || _suppressableBoolean != value) {
            g.writeName(_fastName);
            g.writeBoolean(value);
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
        boolean value;
        try {
            value = _acc.applyAsBoolean(bean);
        } catch (Throwable t) {
            _handleProblem(bean, g, prov, t, true);
            return;
        }
        if (!_suppressableSet || _suppressableBoolean != value) {
            g.writeBoolean(value);
        } else { // important: MUST output a placeholder
            serializeAsOmittedElement(bean, g, prov);
        }
    }
}
