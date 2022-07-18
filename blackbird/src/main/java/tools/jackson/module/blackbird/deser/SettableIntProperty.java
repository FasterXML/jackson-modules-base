package tools.jackson.module.blackbird.deser;

import java.util.function.ObjIntConsumer;

import tools.jackson.core.*;

import tools.jackson.databind.*;
import tools.jackson.databind.deser.SettableBeanProperty;

final class SettableIntProperty
    extends OptimizedSettableBeanProperty<SettableIntProperty>
{
    private static final long serialVersionUID = 1L;
    private ObjIntConsumer<Object> _optimizedSetter;

    public SettableIntProperty(SettableBeanProperty src, ObjIntConsumer<Object> optimizedSetter)
    {
        super(src);
        _optimizedSetter = optimizedSetter;
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableIntProperty(del, _optimizedSetter);
    }

    /*
    /**********************************************************************
    /* Deserialization
    /**********************************************************************
     */

    @Override
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt, Object bean)
        throws JacksonException
    {
        if (!p.isExpectedNumberIntToken()) {
            delegate.deserializeAndSet(p, ctxt, bean);
            return;
        }
        final int v = p.getIntValue();
        try {
            _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            _reportProblem(bean, v, e);
        }
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance)
        throws JacksonException
    {
        if (p.isExpectedNumberIntToken()) {
            return setAndReturn(instance, p.getIntValue());
        }
        return delegate.deserializeSetAndReturn(p, ctxt, instance);
    }

    @Override
    public void set(Object bean, Object value) {
        // not optimal (due to boxing), but better than using reflection:
        int v = ((Number) value).intValue();
        try {
            _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            _reportProblem(bean, v, e);
        }
    }
}
