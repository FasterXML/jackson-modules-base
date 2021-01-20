package com.fasterxml.jackson.module.afterburner.deser;

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

    protected BeanPropertyMutator _propertyMutator;

    protected final int _optimizedIndex;

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

    protected OptimizedSettableBeanProperty(SettableBeanProperty src,
            BeanPropertyMutator mutator, int index)
    {
        super(src);
        _propertyMutator = mutator;
        _optimizedIndex = index;
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

    public abstract SettableBeanProperty withMutator(BeanPropertyMutator mut);

    /*
    /********************************************************************** 
    /* Deserialization, regular
    /********************************************************************** 
     */

    @Override
    public abstract void deserializeAndSet(JsonParser p, DeserializationContext ctxt,
            Object arg2) throws JacksonException;

    @Override
    public abstract void set(Object bean, Object value);

    /*
    /********************************************************************** 
    /* Deserialization, builders
    /********************************************************************** 
     */

    /* !!! 19-Feb-2012, tatu:
     * 
     * We do not yet generate code for these methods: it would not be hugely
     * difficult to add them, but let's first see if anyone needs them...
     * (it is non-trivial, adds code etc, so not without cost).
     * 
     * That is: we'll use Reflection fallback for Builder-based deserialization,
     * so it will not be significantly faster.
     */

    @Override
    public abstract Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance) throws JacksonException;


    @Override
    public Object setAndReturn(Object instance, Object value) {
        return delegate.setAndReturn(instance, value);
    }

    /*
    /********************************************************************** 
    /* Extended API
    /********************************************************************** 
     */

    public int getOptimizedIndex() {
        return _optimizedIndex;
    }

    /*
    /********************************************************************** 
    /* Error handling
    /********************************************************************** 
     */

    /**
     * Helper method called when an exception is throw from mutator, to figure
     * out what to do.
     */
    protected void _reportProblem(Object bean, Object value, Throwable e)
        throws JacksonException
    {
        if ((e instanceof IllegalAccessError)
                || (e instanceof SecurityException)) {
            synchronized (this) {
                // yes, double-locking, so not guaranteed; but all we want here is to reduce likelihood
                // of multiple logging of same underlying problem. Not guaranteed, just improved.
                if (!broken) {
                    broken = true;
                    String msg = String.format("Disabling Afterburner deserialization for %s (field #%d; mutator %s), due to access error (type %s, message=%s)%n",
                            bean.getClass(), _optimizedIndex, getClass().getName(),
                            e.getClass().getName(), e.getMessage());
                    Logger.getLogger(BeanPropertyMutator.class.getName()).log(Level.WARNING, msg, e);
                    // and for next time around, should be re-routed:
                    _propertyMutator = new DelegatingPropertyMutator(delegate);
                }
            }
            delegate.set(bean, value);
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
