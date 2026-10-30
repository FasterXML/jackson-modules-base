package tools.jackson.module.blackbird.deser;

import java.util.logging.Level;
import java.util.logging.Logger;

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
     * Marker that we set once we have logged an access problem with the generated
     * mutator, so it's only logged once; the mutator itself is still used, falling
     * back to the standard one on each such problem.
     */
    private volatile boolean loggedAccessProblem = false;

    /**
     * Type the generated builder setter casts the instance to: the setter's declaring
     * class, or the builder's own class for an inherited protected setter. A builder
     * setter can return an instance of another type, which reflection may still accept.
     */
    protected final Class<?> _receiverType;

    /**
     * Whether this is a property of a builder, whose setter failures are reported
     * by {@link #_reportProblemAndReturn} from every path, {@link #set} included.
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
     * Helper method called when an exception is throw from mutator, to figure
     * out what to do. The parser is only used for a builder's error location; it is
     * the context's parser when called from {@link #set}, as for {@code MethodProperty}.
     */
    protected void _reportProblem(DeserializationContext ctxt, JsonParser p,
            Object bean, Object value, Throwable e)
        throws JacksonException
    {
        // databind calls `set()` on builders too (for properties buffered before a
        // property-based creator, or external type ids), which must behave like
        // `setAndReturn`, regardless of the order of properties in the JSON
        if (_forBuilder) {
            _reportProblemAndReturn(p, value, e);
            return;
        }
        if ((e instanceof IllegalAccessError)
                || (e instanceof SecurityException)) {
            _logAccessProblem(bean, e);
            delegate.set(ctxt, bean, value);
            return;
        }
        if (e instanceof JacksonException) {
            throw (JacksonException) e;
        }
        if (e instanceof Error) {
            throw (Error) e;
        }
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        }
        // how could this ever happen?
        throw new RuntimeException(e);
    }

    /**
     * Like {@link #_reportProblem}, for builders: anything the generated
     * setter throws is reported the way {@code MethodProperty} reports it, and never
     * retried. Telling a failure of the generated accessor itself from one thrown by
     * the setter would mean guessing from the stack trace, and a retry could run the
     * setter twice; setup instead leaves the setters it can't call to reflection.
     * The parser is only used for the error location: the context's parser outside of
     * {@link #deserializeSetAndReturn} and {@link #deserializeAndSet}, as it is
     * for {@code MethodProperty}.
     */
    protected Object _reportProblemAndReturn(JsonParser p, Object value, Throwable e)
        throws JacksonException
    {
        // `MethodProperty` calls the setter through a method handle, which throws
        // whatever the setter throws, and translates that; doing the same keeps type,
        // message, cause and path
        _throwAsJacksonE(p, e, value);
        return null; // never gets here
    }

    private void _logAccessProblem(Object bean, Throwable e) {
        synchronized (this) {
            // yes, double-locking, so not guaranteed; but all we want here is to reduce likelihood
            // of multiple logging of same underlying problem. Not guaranteed, just improved.
            if (!loggedAccessProblem) {
                loggedAccessProblem = true;
                String msg = String.format("Blackbird deserialization for %s (field %s; mutator %s) falling back to reflection, due to access error (type %s, message=%s); further such errors are not logged%n",
                        bean.getClass(), _propName, getClass().getName(),
                        e.getClass().getName(), e.getMessage());
                Logger.getLogger(getClass().getName()).log(Level.WARNING, msg, e);
            }
        }
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
