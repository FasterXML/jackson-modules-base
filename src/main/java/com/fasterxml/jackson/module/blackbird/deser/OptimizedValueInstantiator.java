package com.fasterxml.jackson.module.blackbird.deser;

import java.io.IOException;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdValueInstantiator;

class OptimizedValueInstantiator extends StdValueInstantiator
{
    private static final long serialVersionUID = 1L;
    private final Supplier<?> _optimizedDefaultCreator;
    private final Function<Object[], Object> _optimizedArgsCreator;

    protected OptimizedValueInstantiator(StdValueInstantiator original,
            Supplier<?> defaultCreator, Function<Object[], Object> argsCreator) {
        super(original);
        this._optimizedDefaultCreator = defaultCreator;
        this._optimizedArgsCreator = argsCreator;
    }

    @Override
    public boolean canCreateUsingDefault() {
        return _optimizedDefaultCreator != null || super.canCreateUsingDefault();
    }

    @Override
    public boolean canCreateFromObjectWith() {
        return _optimizedArgsCreator != null || super.canCreateFromObjectWith();
    }

    @Override
    public Object createUsingDefault(DeserializationContext ctxt) throws IOException {
        if (_optimizedDefaultCreator != null) {
            return _optimizedDefaultCreator.get();
        }
        return super.createUsingDefault(ctxt);
    }

    @Override
    public Object createFromObjectWith(DeserializationContext ctxt, Object[] args) throws IOException {
        if (_optimizedArgsCreator != null) {
            return _optimizedArgsCreator.apply(args);
        }
        return super.createFromObjectWith(ctxt, args);
    }
}
