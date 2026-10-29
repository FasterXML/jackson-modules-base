package com.fasterxml.jackson.module.blackbird.deser;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
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

    /**
     * Type the generated builder setter casts the instance to: the setter's declaring
     * class, or the builder's own class for an inherited protected setter. A builder
     * setter can return an instance of another type, which reflection may still accept.
     */
    protected final Class<?> _receiverType;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    protected OptimizedSettableBeanProperty(SettableBeanProperty src, Class<?> receiverType)
    {
        super(src);
        _skipNulls = NullsConstantProvider.isSkipper(_nullProvider);
        _receiverType = receiverType;
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
    protected void _reportProblem(Object bean, Object value, Throwable e)
        throws IOException
    {
        if ((e instanceof IllegalAccessError)
                || (e instanceof SecurityException)) {
            _logAccessProblem(bean, e);
            delegate.set(bean, value);
            return;
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
     * Like {@link #_reportProblem}, for {@link #setAndReturn}: an access failure of
     * the generated accessor falls back to the delegate, and anything the setter
     * throws is reported the way {@code MethodProperty} reports it. The parser is
     * only used for the error location, and is null outside of
     * {@link #deserializeSetAndReturn}, as it is for {@code MethodProperty}.
     */
    protected Object _reportProblemAndReturn(JsonParser p, Object accessor, Object bean, Object value,
            Throwable e)
        throws IOException
    {
        if (_isAccessorAccessFailure(accessor, bean, e)) {
            _logAccessProblem(bean, e);
            return _reflectiveSetAndReturn(p, bean, value);
        }
        // `Method.invoke` wraps whatever the setter throws, and `MethodProperty`
        // translates that wrapper; doing the same keeps type, message, cause and path
        _throwAsIOE(p, new InvocationTargetException(e), value);
        return null; // never gets here
    }

    /**
     * Calls the setter the way {@code MethodProperty.deserializeSetAndReturn} does once
     * it has the value, so a failure is reported the same way, with the parser's
     * location if {@code p} isn't null. For a value or builder the generated setter
     * can't take, once the value has been read.
     */
    protected Object _reflectiveSetAndReturn(JsonParser p, Object instance, Object value)
        throws IOException
    {
        Member setter = getMember().getMember();
        if (!(setter instanceof Method)) {
            return delegate.setAndReturn(instance, value);
        }
        try {
            Object result = ((Method) setter).invoke(instance, value);
            return (result == null) ? instance : result;
        } catch (Exception e) {
            _throwAsIOE(p, e, value);
            return null; // never gets here
        }
    }

    // Retrying is only safe if the setter never ran, so require positive evidence:
    // the JVM raises an access failure in the accessor's own frame, which is then
    // on top of the stack (or, where generated frames are hidden from stack traces,
    // this property's frame that called it). A throwable created anywhere else,
    // such as one the setter saved and throws later, keeps its original failure.
    private boolean _isAccessorAccessFailure(Object accessor, Object bean, Throwable e) {
        if (!(e instanceof IllegalAccessError) && !(e instanceof SecurityException)) {
            return false;
        }
        StackTraceElement[] trace;
        try {
            trace = e.getStackTrace();
        } catch (RuntimeException traceFailure) {
            // a subclass can override it; that is no evidence either, and mustn't
            // replace the setter's failure
            return false;
        }
        // No trace (stack traces disabled, or a stackless throwable) is no evidence;
        // setup already skips the accessors known to fail this way
        if ((trace == null) || (trace.length == 0) || (trace[0] == null)) {
            return false;
        }
        String top = trace[0].getClassName();
        if (top.equals(accessor.getClass().getName())) {
            return true;
        }
        // A setter in a hidden class is left out of the trace too, which is only
        // possible if the bean's own class is hidden (their names contain a '/')
        return top.equals(getClass().getName()) && (bean.getClass().getName().indexOf('/') < 0);
    }

    private void _logAccessProblem(Object bean, Throwable e) {
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
