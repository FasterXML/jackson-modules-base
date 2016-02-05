package com.fasterxml.jackson.module.afterburner.deser;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdValueInstantiator;

/**
 * Base class for concrete bytecode-generated value instantiators.
 */
public abstract class OptimizedValueInstantiator
    extends StdValueInstantiator
{
    private static final long serialVersionUID = 1L;

    /**
     * Default constructor which is only used when creating
     * dummy instance to call factory method.
     */
    protected OptimizedValueInstantiator() {
        super(/*DeserializationConfig*/null, (Class<?>)String.class);
    }

    /**
     * Copy-constructor to use for creating actual optimized instances.
     */
    protected OptimizedValueInstantiator(StdValueInstantiator src) {
        super(src);
    }

    /**
     * Need to override this, now that we have installed default creator.
     */
    @Override
    public boolean canCreateUsingDefault() {
        return true;
    }
    
    protected abstract OptimizedValueInstantiator with(StdValueInstantiator src);

    /* Define as abstract to ensure that it gets reimplemented; or if not,
     * we get a specific error (too easy to break, and get cryptic error)
     */
    @Override
    public abstract Object createUsingDefault(DeserializationContext ctxt)
            throws IOException, JsonProcessingException;
}
