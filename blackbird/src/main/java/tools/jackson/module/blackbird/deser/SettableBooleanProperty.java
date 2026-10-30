package tools.jackson.module.blackbird.deser;


import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.module.blackbird.deser.BBDeserializerModifier.ObjBooleanBiFunction;

final class SettableBooleanProperty
    extends OptimizedSettableBeanProperty<SettableBooleanProperty>
{
    private static final long serialVersionUID = 1L;
    private ObjBooleanConsumer _optimizedSetter;
    private ObjBooleanBiFunction _optimizedSetterAndReturn;

    public SettableBooleanProperty(SettableBeanProperty src, ObjBooleanConsumer optimizedSetter,
            ObjBooleanBiFunction optimizedSetterAndReturn, Class<?> receiverType, boolean forBuilder)
    {
        super(src, receiverType, forBuilder);
        _optimizedSetter = optimizedSetter;
        _optimizedSetterAndReturn = optimizedSetterAndReturn;
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableBooleanProperty(del, _optimizedSetter, _optimizedSetterAndReturn, _receiverType, _forBuilder);
    }

    /*
    /**********************************************************************
    /* Deserialization
    /**********************************************************************
     */

    @Override
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt, Object bean) throws JacksonException {
        boolean b;
        JsonToken t = p.currentToken();
        if (t == JsonToken.VALUE_TRUE) {
            b = true;
        } else if (t == JsonToken.VALUE_FALSE) {
            b = false;
        } else {
            delegate.deserializeAndSet(p, ctxt, bean);
            return;
        }
        try {
            _optimizedSetter.accept(bean, b);
        } catch (Throwable e) {
            _reportProblem(ctxt, p, bean, b, e);
        }
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance) throws JacksonException
    {
        if ((_optimizedSetterAndReturn != null) && _receiverType.isInstance(instance)) {
            JsonToken t = p.currentToken();
            if (t == JsonToken.VALUE_TRUE) {
                return _setAndReturn(p, instance, true);
            }
            if (t == JsonToken.VALUE_FALSE) {
                return _setAndReturn(p, instance, false);
            }
        }
        return delegate.deserializeSetAndReturn(p, ctxt, instance);
    }

    @Override
    public void set(DeserializationContext ctxt, Object bean, Object value) throws JacksonException {
        // the delegate skips a null, or reports it; and anything else (another type,
        // which reflection may widen or reject) or a builder the generated setter
        // can't take is reported, or set, as reflection does
        if (!(value instanceof Boolean) || (_forBuilder && !_receiverType.isInstance(bean))) {
            delegate.set(ctxt, bean, value);
            return;
        }
        // not optimal (due to boxing), but better than using reflection:
        final boolean b = ((Boolean) value).booleanValue();
        try {
            _optimizedSetter.accept(bean, b);
        } catch (Throwable e) {
            _reportProblem(ctxt, ctxt.getParser(), bean, b, e);
        }
    }

    @Override
    public Object setAndReturn(DeserializationContext ctxt, Object instance, Object value) throws JacksonException {
        // a setter only reflection may call, anything else (null, or another type that
        // reflection may widen or reject), or an instance the generated setter can't take,
        // is left to the delegate
        if ((_optimizedSetterAndReturn == null) || !(value instanceof Boolean)
                || !_receiverType.isInstance(instance)) {
            return delegate.setAndReturn(ctxt, instance, value);
        }
        // not optimal (due to boxing), but better than using reflection:
        return _setAndReturn(ctxt.getParser(), instance, ((Boolean) value).booleanValue());
    }

    private Object _setAndReturn(JsonParser p, Object instance, boolean b) throws JacksonException {
        try {
            Object result = _optimizedSetterAndReturn.apply(instance, b);
            return (result == null) ? instance : result;
        } catch (Throwable e) {
            return _reportProblemAndReturn(p, b, e);
        }
    }
}
