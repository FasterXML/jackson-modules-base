package com.fasterxml.jackson.module.afterburner;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.module.afterburner.ser.ABSerializerModifier;
import com.fasterxml.jackson.module.afterburner.deser.ABDeserializerModifier;

public class AfterburnerModule extends Module
    implements java.io.Serializable // is this necessary?
{
    private static final long serialVersionUID = 1L;

    /*
    /********************************************************************** 
    /* Configuration settings
    /********************************************************************** 
     */

    /**
     * Flag to indicate whether we will try to load generated classes using
     * same class loader as one that loaded class being accessed or not.
     * If not, we will use class loader that loaded this module.
     * Benefit of using value class loader is that 'protected' and 'package access'
     * properties can be accessed; otherwise only 'public' properties can
     * be accessed.
     *<p>
     * By default this feature is enabled.
     */
    protected boolean _cfgUseValueClassLoader = true;

    /**
     * Flag to indicate whether we should use an optimized sub-class of
     * {@link com.fasterxml.jackson.databind.deser.BeanDeserializer} or not.
     * Use of optimized version should further improve performance, but
     * it can be disabled in case it causes issues.
     *<p>
     * By default this feature is enabled.
     */
    protected boolean _cfgUseOptimizedBeanDeserializer = true;
    
    /*
    /********************************************************************** 
    /* Basic life-cycle
    /********************************************************************** 
     */
    
    public AfterburnerModule() { }

    @Override
    public void setupModule(SetupContext context)
    {
        ClassLoader cl = _cfgUseValueClassLoader ? null : getClass().getClassLoader();
        context.addDeserializerModifier(new ABDeserializerModifier(cl,
                _cfgUseOptimizedBeanDeserializer));
        context.addSerializerModifier(new ABSerializerModifier(cl));
    }

    @Override
    public String getModuleName() {
        return getClass().getSimpleName();
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /*
    /********************************************************************** 
    /* Config methods
    /********************************************************************** 
     */

    /**
     * Flag to indicate whether we will try to load generated classes using
     * same class loader as one that loaded class being accessed or not.
     * If not, we will use class loader that loaded this module.
     * Benefit of using value class loader is that 'protected' and 'package access'
     * properties can be accessed; otherwise only 'public' properties can
     * be accessed.
     *<p>
     * By default this feature is enabled.
     */
    public AfterburnerModule setUseValueClassLoader(boolean state) {
        _cfgUseValueClassLoader = state;
        return this;
    }

    public AfterburnerModule setUseOptimizedBeanDeserializer(boolean state) {
        _cfgUseOptimizedBeanDeserializer = state;
        return this;
    }
}

