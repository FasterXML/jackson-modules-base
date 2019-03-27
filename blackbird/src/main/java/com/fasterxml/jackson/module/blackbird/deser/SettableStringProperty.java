package com.fasterxml.jackson.module.blackbird.deser;

import java.io.IOException;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

final class SettableStringProperty
    extends OptimizedSettableBeanProperty<SettableStringProperty>
{
    private static final long serialVersionUID = 1L;
    private final BiConsumer<Object, String> _optimizedSetter;

    public SettableStringProperty(SettableBeanProperty src, BiConsumer<Object, String> optimizedSetter)
    {
        super(src);
        _optimizedSetter = optimizedSetter;
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableStringProperty(del, _optimizedSetter);
    }

    /*
    /**********************************************************************
    /* Deserialization
    /**********************************************************************
     */

    // Copied from StdDeserializer.StringDeserializer:
    @Override
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt, Object bean) throws IOException
    {
        String text;
        if (p.hasToken(JsonToken.VALUE_NULL)) {
            if (_skipNulls) {
                return;
            }
            text = (String) _nullProvider.getNullValue(ctxt);
        } else {
            text = p.getValueAsString();
            if (text == null) {
                text = _deserializeString(p, ctxt);
            }
        }
        set(bean, text);
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p, DeserializationContext ctxt, Object instance) throws IOException
    {
        String text;
        if (p.hasToken(JsonToken.VALUE_NULL)) {
            if (_skipNulls) {
                return instance;
            }
            text = (String) _nullProvider.getNullValue(ctxt);
        } else {
            text = p.getValueAsString();
            if (text == null) {
                text = _deserializeString(p, ctxt);
            }
        }
        return setAndReturn(instance, text);
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        try {
            _optimizedSetter.accept(bean, (String) value);
        } catch (Throwable e) {
            _reportProblem(bean, value, e);
        }
    }
}
