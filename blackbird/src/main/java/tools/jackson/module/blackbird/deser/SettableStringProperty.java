package tools.jackson.module.blackbird.deser;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.deser.SettableBeanProperty;

final class SettableStringProperty
    extends OptimizedSettableBeanProperty<SettableStringProperty>
{
    private static final long serialVersionUID = 1L;
    private final BiConsumer<Object, String> _optimizedSetter;
    private final BiFunction<Object, Object, Object> _optimizedSetterAndReturn;

    public SettableStringProperty(SettableBeanProperty src, BiConsumer<Object, String> optimizedSetter,
            BiFunction<Object, Object, Object> optimizedSetterAndReturn, Class<?> receiverType, boolean forBuilder)
    {
        super(src, receiverType, forBuilder);
        _optimizedSetter = optimizedSetter;
        _optimizedSetterAndReturn = optimizedSetterAndReturn;
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty del) {
        return new SettableStringProperty(del, _optimizedSetter, _optimizedSetterAndReturn, _receiverType, _forBuilder);
    }

    /*
    /**********************************************************************
    /* Deserialization
    /**********************************************************************
     */

    // Copied from StdDeserializer.StringDeserializer:
    @Override
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt, Object bean) throws JacksonException
    {
        if (!p.hasToken(JsonToken.VALUE_STRING)) {
            delegate.deserializeAndSet(p, ctxt, bean);
            return;
        }
        _set(ctxt, p, bean, p.getString());
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p, DeserializationContext ctxt, Object instance) throws JacksonException
    {
        if ((_optimizedSetterAndReturn != null) && _receiverType.isInstance(instance)
                && p.hasToken(JsonToken.VALUE_STRING)) {
            String text = p.getString();
            // some parsers give null here (e.g. for a `TextNode(null)`), which needs
            // the delegate's null handling
            if (text != null) {
                return _setAndReturn(p, instance, text);
            }
        }
        return delegate.deserializeSetAndReturn(p, ctxt, instance);
    }

    @Override
    public void set(DeserializationContext ctxt, Object bean, Object value) throws JacksonException {
        // as in `MethodProperty`
        if ((value == null) && _skipNulls) {
            return;
        }
        // a value of another type, or a builder the generated setter can't take,
        // is left to the delegate, which reports it
        if (((value != null) && !(value instanceof String))
                || (_forBuilder && !_receiverType.isInstance(bean))) {
            delegate.set(ctxt, bean, value);
            return;
        }
        _set(ctxt, ctxt.getParser(), bean, value);
    }

    private void _set(DeserializationContext ctxt, JsonParser p, Object bean, Object value) throws JacksonException {
        try {
            _optimizedSetter.accept(bean, (String) value);
        } catch (Throwable e) {
            _reportProblem(ctxt, p, bean, value, e);
        }
    }

    @Override
    public Object setAndReturn(DeserializationContext ctxt, Object instance, Object value) throws JacksonException {
        // a value of another type, or an instance the generated setter can't take,
        // is left to the delegate, which reports it
        if ((_optimizedSetterAndReturn == null) || !_receiverType.isInstance(instance)
                || ((value != null) && !(value instanceof String))) {
            return delegate.setAndReturn(ctxt, instance, value);
        }
        // as in `MethodProperty`, only here: `deserializeSetAndReturn` already skipped
        // the nulls it should
        if ((value == null) && _skipNulls) {
            return instance;
        }
        return _setAndReturn(ctxt.getParser(), instance, value);
    }

    private Object _setAndReturn(JsonParser p, Object instance, Object value) throws JacksonException {
        try {
            Object result = _optimizedSetterAndReturn.apply(instance, value);
            return (result == null) ? instance : result;
        } catch (Throwable e) {
            return _reportProblemAndReturn(p, value, e);
        }
    }
}
