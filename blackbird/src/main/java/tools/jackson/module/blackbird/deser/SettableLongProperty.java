package tools.jackson.module.blackbird.deser;

import java.util.function.ObjLongConsumer;

import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.deser.SettableBeanProperty;

final class SettableLongProperty
    extends OptimizedSettableBeanProperty<SettableLongProperty>
{
    private static final long serialVersionUID = 1L;
    private ObjLongConsumer<Object> _optimizedSetter;

    public SettableLongProperty(SettableBeanProperty src, ObjLongConsumer<Object> optimizedSetter)
    {
        super(src);
        _optimizedSetter = optimizedSetter;
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableLongProperty(del, _optimizedSetter);
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
        final long v = p.getLongValue();
        try {
           _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            _reportProblem(ctxt, bean, v, e);
        }
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance)
        throws JacksonException
    {
        if (p.isExpectedNumberIntToken()) {
            return setAndReturn(ctxt, instance, p.getLongValue());
        }
        return delegate.deserializeSetAndReturn(p, ctxt, instance);
    }

    @Override
    public void set(DeserializationContext ctxt, Object bean, Object value)
    {
        // not optimal (due to boxing), but better than using reflection:
        final long v = ((Number) value).longValue();
        try {
            _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            _reportProblem(ctxt, bean, v, e);
        }
    }
}
