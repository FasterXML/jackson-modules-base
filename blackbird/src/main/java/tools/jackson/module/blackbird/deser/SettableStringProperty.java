package tools.jackson.module.blackbird.deser;

import java.util.function.BiConsumer;

import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.deser.SettableBeanProperty;

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
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt, Object bean)
        throws JacksonException
    {
        if (!p.hasToken(JsonToken.VALUE_STRING)) {
            delegate.deserializeAndSet(p, ctxt, bean);
            return;
        }
        set(ctxt, bean, p.getText());
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p, DeserializationContext ctxt, Object instance)
        throws JacksonException
    {
        if (p.hasToken(JsonToken.VALUE_STRING)) {
            return setAndReturn(ctxt, instance, p.getText());
        }
        return delegate.deserializeSetAndReturn(p, ctxt, instance);
    }

    @Override
    public void set(DeserializationContext ctxt, Object bean, Object value) {
        try {
            _optimizedSetter.accept(bean, (String) value);
        } catch (Throwable e) {
            _reportProblem(ctxt, bean, value, e);
        }
    }
}
