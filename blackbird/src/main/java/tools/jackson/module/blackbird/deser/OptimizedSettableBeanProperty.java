package tools.jackson.module.blackbird.deser;

import tools.jackson.core.*;

import tools.jackson.databind.*;
import tools.jackson.databind.deser.*;
import tools.jackson.databind.deser.impl.NullsConstantProvider;
import tools.jackson.databind.util.ClassUtil;

/**
 * Base class for concrete type-specific {@link SettableBeanProperty}
 * implementations.
 */
abstract class OptimizedSettableBeanProperty<T extends OptimizedSettableBeanProperty<T>>
    extends SettableBeanProperty.Delegating
{
    private static final long serialVersionUID = 1L;

    /**
     * @since 2.9
     */
    final protected boolean _skipNulls;

    /**
     * Type the generated builder setter casts the instance to: the setter's declaring
     * class, or the builder's own class for an inherited protected setter. A builder
     * setter can return an instance of another type, which reflection may still accept.
     */
    protected final Class<?> _receiverType;

    /**
     * Whether this is a property of a builder; if so, {@link #set} passes instances
     * the generated setter can't accept (see {@link #_receiverType}) to the delegate.
     */
    protected final boolean _forBuilder;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    protected OptimizedSettableBeanProperty(SettableBeanProperty src, Class<?> receiverType,
            boolean forBuilder)
    {
        super(src);
        _skipNulls = NullsConstantProvider.isSkipper(_nullProvider);
        _receiverType = receiverType;
        _forBuilder = forBuilder;
    }

    // Base impl of `withName()` fine as-is:
    // public SettableBeanProperty withName(PropertyName name);

    // But value deserializer handling needs some more care
    @Override
    public final SettableBeanProperty withValueDeserializer(ValueDeserializer<?> deser) {
        SettableBeanProperty newDelegate = delegate.withValueDeserializer(deser);
        if (newDelegate == delegate) {
            return this;
        }
        if (!_isDefaultDeserializer(deser)) {
            return newDelegate;
        }
        return withDelegate(newDelegate);
    }

    @Override
    protected abstract SettableBeanProperty withDelegate(SettableBeanProperty d);

    /*
    /**********************************************************************
    /* Deserialization, regular
    /**********************************************************************
     */

    @Override
    public abstract void deserializeAndSet(JsonParser p, DeserializationContext ctxt, Object arg2)
        throws JacksonException;

    @Override
    public abstract void set(DeserializationContext ctxt, Object bean, Object value);

    /*
    /**********************************************************************
    /* Deserialization, builders
    /**********************************************************************
     */

    // These use the generated accessor and keep the builder it returns (the same
    // one for a void setter); setters only reflection may call go through the delegate.

    @Override
    public abstract Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance)
        throws JacksonException;

    @Override
    public abstract Object setAndReturn(DeserializationContext ctxt, Object instance, Object value);

    /*
    /**********************************************************************
    /* Error handling
    /**********************************************************************
     */

    /**
     * Helper method called when an exception is thrown from mutator, to be thrown
     * by the caller (which keeps the compiler aware it never returns): anything the
     * generated setter throws is reported the way {@code MethodProperty} reports it,
     * and never retried. Telling a failure of the generated accessor itself from one
     * thrown by the setter would mean guessing from the stack trace, and a retry could
     * run the setter twice; setup instead leaves the setters it can't call to reflection.
     * The parser is only used for the error location: the context's parser outside of
     * {@link #deserializeSetAndReturn} and {@link #deserializeAndSet}, as it is
     * for {@code MethodProperty}.
     */
    protected JacksonException _reportProblem(JsonParser p, Object value, Throwable e)
        throws JacksonException
    {
        // `MethodProperty` calls the setter through a method handle, which throws
        // whatever the setter throws, and translates that; doing the same keeps type,
        // message, cause and path
        _throwAsJacksonE(p, e, value);
        // never gets here, as `_throwAsJacksonE` always throws
        return DatabindException.from(p, ClassUtil.exceptionMessage(e), e);
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    /**
     * Helper method used to check whether given deserializer is the default
     * deserializer implementation: this is necessary to avoid overriding custom
     * deserializers.
     */
    protected boolean _isDefaultDeserializer(ValueDeserializer<?> deser) {
        return (deser == null)
                || (deser instanceof SuperSonicBeanDeserializer)
                || ClassUtil.isJacksonStdImpl(deser);
    }
}
