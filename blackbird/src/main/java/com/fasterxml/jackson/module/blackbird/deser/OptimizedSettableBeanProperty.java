package com.fasterxml.jackson.module.blackbird.deser;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.*;
import com.fasterxml.jackson.databind.deser.impl.NullsConstantProvider;
import com.fasterxml.jackson.databind.util.ClassUtil;

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
    public final SettableBeanProperty withValueDeserializer(JsonDeserializer<?> deser) {
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
    public abstract void deserializeAndSet(JsonParser jp, DeserializationContext ctxt,
            Object arg2) throws IOException;

    @Override
    public abstract void set(Object bean, Object value) throws IOException;

    /*
    /**********************************************************************
    /* Deserialization, builders
    /**********************************************************************
     */

    // These use the generated accessor and keep the builder it returns (the same
    // one for a void setter); setters only reflection may call go through the delegate.

    @Override
    public abstract Object deserializeSetAndReturn(JsonParser jp,
            DeserializationContext ctxt, Object instance) throws IOException;

    @Override
    public abstract Object setAndReturn(Object instance, Object value) throws IOException;

    /*
    /**********************************************************************
    /* Error handling
    /**********************************************************************
     */

    /**
     * Helper method called when an exception is throw from mutator, to figure
     * out what to do. The parser is only used for the error location, and is null
     * when called from {@link #set}.
     *
     * @since 2.9
     */
    protected void _reportProblem(JsonParser p, Object value, Throwable e)
        throws IOException
    {
        // databind calls `set()` on builders too (for properties buffered before a
        // property-based creator, or external type ids), which must behave like
        // `setAndReturn`, regardless of the order of properties in the JSON
        if (_forBuilder) {
            _reportProblemAndReturn(p, e);
            return;
        }
        // Never retried, as the setter may already have run (see
        // `_reportProblemAndReturn`). These used to be retried through reflection,
        // which reported the setter's failure the way `MethodProperty` does (`p` is
        // only for the location); keep that, rather than letting an `Error` escape
        // unwrapped
        if ((e instanceof IllegalAccessError) || (e instanceof SecurityException)) {
            _throwAsIOE(p, new InvocationTargetException(e), value);
        }
        if (e instanceof IOException) {
            throw (IOException) e;
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
     * The parser is only used for the error location, and is null outside of
     * {@link #deserializeSetAndReturn} and {@link #deserializeAndSet}, as it is
     * for {@code MethodProperty}.
     */
    protected Object _reportProblemAndReturn(JsonParser p, Throwable e)
        throws IOException
    {
        // `Method.invoke` wraps whatever the setter throws, and `MethodProperty`
        // translates that wrapper; doing the same keeps type, message, cause and path
        throw _throwAsIOE(p, new InvocationTargetException(e));
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
    protected boolean _isDefaultDeserializer(JsonDeserializer<?> deser) {
        return (deser == null)
                || (deser instanceof SuperSonicBeanDeserializer)
                || ClassUtil.isJacksonStdImpl(deser);
    }
}
