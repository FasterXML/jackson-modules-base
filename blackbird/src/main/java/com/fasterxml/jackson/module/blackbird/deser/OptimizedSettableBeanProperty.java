package com.fasterxml.jackson.module.blackbird.deser;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

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
    protected void _reportProblem(JsonParser p, Object value, Throwable e)
        throws IOException
    {
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
     * Like {@link #_reportProblem}, for {@link #setAndReturn}: anything the generated
     * setter throws is reported the way {@code MethodProperty} reports it, and never
     * retried. Telling a failure of the generated accessor itself from one thrown by
     * the setter would mean guessing from the stack trace, and a retry could run the
     * setter twice; setup instead leaves the setters it can't call to reflection.
     * The parser is only used for the error location, and is null outside of
     * {@link #deserializeSetAndReturn}, as it is for {@code MethodProperty}.
     */
    protected Object _reportProblemAndReturn(JsonParser p, Object value, Throwable e)
        throws IOException
    {
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
