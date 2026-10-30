package com.fasterxml.jackson.module.blackbird.deser;

import java.io.IOException;
import java.util.function.ObjIntConsumer;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.module.blackbird.deser.BBDeserializerModifier.ObjIntBiFunction;

final class SettableIntProperty
    extends OptimizedSettableBeanProperty<SettableIntProperty>
{
    private static final long serialVersionUID = 1L;
    private ObjIntConsumer<Object> _optimizedSetter;
    private ObjIntBiFunction _optimizedSetterAndReturn;

    public SettableIntProperty(SettableBeanProperty src, ObjIntConsumer<Object> optimizedSetter,
            ObjIntBiFunction optimizedSetterAndReturn, Class<?> receiverType, boolean forBuilder)
    {
        super(src, receiverType, forBuilder);
        _optimizedSetter = optimizedSetter;
        _optimizedSetterAndReturn = optimizedSetterAndReturn;
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableIntProperty(del, _optimizedSetter, _optimizedSetterAndReturn, _receiverType, _forBuilder);
    }

    /*
    /**********************************************************************
    /* Deserialization
    /**********************************************************************
     */

    @Override
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt,
            Object bean) throws IOException
    {
        if (!p.isExpectedNumberIntToken()) {
            delegate.deserializeAndSet(p, ctxt, bean);
            return;
        }
        final int v = p.getIntValue();
        try {
            _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            _reportProblem(p, bean, v, e);
        }
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance)
        throws IOException
    {
        if ((_optimizedSetterAndReturn != null) && _receiverType.isInstance(instance)
                && p.isExpectedNumberIntToken()) {
            return _setAndReturn(p, instance, p.getIntValue());
        }
        return delegate.deserializeSetAndReturn(p, ctxt, instance);
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        // the delegate skips a null, or reports it; and anything else (another type,
        // which reflection may widen or reject) or an instance the generated setter
        // can't take is reported, or set, as reflection does
        if (!(value instanceof Integer) || !_receiverType.isInstance(bean)) {
            delegate.set(bean, value);
            return;
        }
        // not optimal (due to boxing), but better than using reflection:
        int v = ((Number) value).intValue();
        try {
            _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            _reportProblem(null, bean, v, e);
        }
    }

    @Override
    public Object setAndReturn(Object instance, Object value) throws IOException {
        // a setter only reflection may call, anything else (null, or another type that
        // reflection may widen or reject), or an instance the generated setter can't take,
        // is left to the delegate
        if ((_optimizedSetterAndReturn == null) || !(value instanceof Integer)
                || !_receiverType.isInstance(instance)) {
            return delegate.setAndReturn(instance, value);
        }
        // not optimal (due to boxing), but better than using reflection:
        return _setAndReturn(null, instance, ((Integer) value).intValue());
    }

    private Object _setAndReturn(JsonParser p, Object instance, int v) throws IOException {
        try {
            Object result = _optimizedSetterAndReturn.apply(instance, v);
            return (result == null) ? instance : result;
        } catch (Throwable e) {
            return _reportProblemAndReturn(p, e);
        }
    }
}
