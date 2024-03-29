package com.fasterxml.jackson.module.mrbean;

import com.fasterxml.jackson.core.Version;

public class MrBeanModule extends com.fasterxml.jackson.databind.Module
{
    /**
     * Configured materializer instance to register with deserializer factory.
     */
    protected AbstractTypeMaterializer _materializer;
    
    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */
    
    public MrBeanModule() {
        this(new AbstractTypeMaterializer());
    }

    public MrBeanModule(AbstractTypeMaterializer materializer) {
        _materializer = materializer;
    }

    @Override
    public String getModuleName() {
        return getClass().getSimpleName();
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }
    
    @Override
    public void setupModule(SetupContext context)
    {
        // All we really need to for now is to register materializer:
        context.addAbstractTypeResolver(_materializer);
    }

    /*
    /**********************************************************
    /* Extended API
    /**********************************************************
     */

    /**
     * Accessor for getting internal {@link AbstractTypeMaterializer}.
     */
    public AbstractTypeMaterializer getMaterializer() {
        return _materializer;
    }
}
