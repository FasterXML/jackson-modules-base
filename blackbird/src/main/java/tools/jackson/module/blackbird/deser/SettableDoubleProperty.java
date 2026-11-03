package tools.jackson.module.blackbird.deser;

import java.util.function.ObjDoubleConsumer;

import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.module.blackbird.deser.BBDeserializerModifier.ObjDoubleBiFunction;

final class SettableDoubleProperty
    extends OptimizedSettableBeanProperty<SettableDoubleProperty>
{
    private static final long serialVersionUID = 1L;
    private ObjDoubleConsumer<Object> _optimizedSetter;
    private ObjDoubleBiFunction _optimizedSetterAndReturn;

    public SettableDoubleProperty(SettableBeanProperty src, ObjDoubleConsumer<Object> optimizedSetter,
            ObjDoubleBiFunction optimizedSetterAndReturn, Class<?> receiverType, boolean forBuilder)
    {
        super(src, receiverType, forBuilder);
        _optimizedSetter = optimizedSetter;
        _optimizedSetterAndReturn = optimizedSetterAndReturn;
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableDoubleProperty(del, _optimizedSetter, _optimizedSetterAndReturn, _receiverType, _forBuilder);
    }

    /*
    /**********************************************************************
    /* Deserialization
    /**********************************************************************
     */

    // Only a float token is read directly, as the default deserializer reads it
    // (without coercion checks); anything else, including integers, Strings (like
    // "NaN") and nulls, is left to the delegate and its coercion rules

    @Override
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt,
            Object bean) throws JacksonException
    {
        if (!p.hasToken(JsonToken.VALUE_NUMBER_FLOAT)) {
            delegate.deserializeAndSet(p, ctxt, bean);
            return;
        }
        final double v = p.getDoubleValue();
        try {
           _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            throw _reportProblem(p, v, e);
        }
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance) throws JacksonException
    {
        if ((_optimizedSetterAndReturn != null) && _receiverType.isInstance(instance)
                && p.hasToken(JsonToken.VALUE_NUMBER_FLOAT)) {
            return _setAndReturn(p, instance, p.getDoubleValue());
        }
        return delegate.deserializeSetAndReturn(p, ctxt, instance);
    }

    @Override
    public void set(DeserializationContext ctxt, Object bean, Object value) throws JacksonException {
        // the delegate skips a null, or reports it; and anything else (another type,
        // which reflection may widen or reject) or a builder the generated setter
        // can't take is reported, or set, as reflection does
        if (!(value instanceof Double) || (_forBuilder && !_receiverType.isInstance(bean))) {
            delegate.set(ctxt, bean, value);
            return;
        }
        // not optimal (due to boxing), but better than using reflection:
        final double v = ((Double) value).doubleValue();
        try {
            _optimizedSetter.accept(bean, v);
        } catch (Throwable e) {
            throw _reportProblem(ctxt.getParser(), v, e);
        }
    }

    @Override
    public Object setAndReturn(DeserializationContext ctxt, Object instance, Object value) throws JacksonException {
        // a setter only reflection may call, anything else (null, or another type that
        // reflection may widen or reject), or an instance the generated setter can't take,
        // is left to the delegate
        if ((_optimizedSetterAndReturn == null) || !(value instanceof Double)
                || !_receiverType.isInstance(instance)) {
            return delegate.setAndReturn(ctxt, instance, value);
        }
        // not optimal (due to boxing), but better than using reflection:
        return _setAndReturn(ctxt.getParser(), instance, ((Double) value).doubleValue());
    }

    private Object _setAndReturn(JsonParser p, Object instance, double v) throws JacksonException {
        try {
            Object result = _optimizedSetterAndReturn.apply(instance, v);
            return (result == null) ? instance : result;
        } catch (Throwable e) {
            throw _reportProblem(p, v, e);
        }
    }
}
