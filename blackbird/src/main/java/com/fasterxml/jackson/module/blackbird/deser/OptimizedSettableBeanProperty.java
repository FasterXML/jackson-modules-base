package com.fasterxml.jackson.module.blackbird.deser;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

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
     * Marker that we set if mutator turns out to be broken in a systematic
     * way that we can handle by redirecting it back to standard one.
     */
    private volatile boolean broken = false;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    protected OptimizedSettableBeanProperty(SettableBeanProperty src)
    {
        super(src);
        _skipNulls = NullsConstantProvider.isSkipper(_nullProvider);
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

    // Setters that return a value use the generated accessor and keep the
    // returned builder; void setters still go through the delegate.

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
     * out what to do.
     *
     * @since 2.9
     */
    protected Object _reportProblem(Object bean, Object value, Throwable e)
        throws IOException
    {
        if ((e instanceof IllegalAccessError)
                || (e instanceof SecurityException)) {
            synchronized (this) {
                // yes, double-locking, so not guaranteed; but all we want here is to reduce likelihood
                // of multiple logging of same underlying problem. Not guaranteed, just improved.
                if (!broken) {
                    broken = true;
                    String msg = String.format("Disabling Blackbird deserialization for %s (field %s; mutator %s), due to access error (type %s, message=%s)%n",
                            bean.getClass(), _propName, getClass().getName(),
                            e.getClass().getName(), e.getMessage());
                    Logger.getLogger(getClass().getName()).log(Level.WARNING, msg, e);
                }
            }
            // delegate is a MethodProperty: same as `set()`, but also returns the builder
            return delegate.setAndReturn(bean, value);
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
