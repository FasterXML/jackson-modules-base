package tools.jackson.module.blackbird.deser;

import java.util.function.ObjIntConsumer;

import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.module.blackbird.deser.BBDeserializerModifier.ObjIntBiFunction;

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
            Object bean) throws JacksonException
    {
        if (!p.isExpectedNumberIntToken()) {
            delegate.deserializeAndSet(p, ctxt, bean);
            return;
        }
        final int v = p.getIntValue();
        try {
            _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            _reportProblem(ctxt, p, bean, v, e);
        }
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance)
        throws JacksonException
    {
        if ((_optimizedSetterAndReturn != null) && _receiverType.isInstance(instance)
                && p.isExpectedNumberIntToken()) {
            return _setAndReturn(p, instance, p.getIntValue());
        }
        return delegate.deserializeSetAndReturn(p, ctxt, instance);
    }

    @Override
    public void set(DeserializationContext ctxt, Object bean, Object value) throws JacksonException {
        // the delegate skips a null, or reports it; and anything else (another type,
        // which reflection may widen or reject) or a builder the generated setter
        // can't take is reported, or set, as reflection does
        if (!(value instanceof Integer) || (_forBuilder && !_receiverType.isInstance(bean))) {
            delegate.set(ctxt, bean, value);
            return;
        }
        // not optimal (due to boxing), but better than using reflection:
        int v = ((Number) value).intValue();
        try {
            _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            _reportProblem(ctxt, ctxt.getParser(), bean, v, e);
        }
    }

    @Override
    public Object setAndReturn(DeserializationContext ctxt, Object instance, Object value) throws JacksonException {
        // a setter only reflection may call, anything else (null, or another type that
        // reflection may widen or reject), or an instance the generated setter can't take,
        // is left to the delegate
        if ((_optimizedSetterAndReturn == null) || !(value instanceof Integer)
                || !_receiverType.isInstance(instance)) {
            return delegate.setAndReturn(ctxt, instance, value);
        }
        // not optimal (due to boxing), but better than using reflection:
        return _setAndReturn(ctxt.getParser(), instance, ((Integer) value).intValue());
    }

    private Object _setAndReturn(JsonParser p, Object instance, int v) throws JacksonException {
        try {
            Object result = _optimizedSetterAndReturn.apply(instance, v);
            return (result == null) ? instance : result;
        } catch (Throwable e) {
            return _reportProblemAndReturn(p, v, e);
        }
    }
}
