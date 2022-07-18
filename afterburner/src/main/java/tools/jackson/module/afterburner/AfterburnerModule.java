package tools.jackson.module.afterburner;

import tools.jackson.core.Version;

import tools.jackson.databind.JacksonModule;

import tools.jackson.module.afterburner.deser.ABDeserializerModifier;
import tools.jackson.module.afterburner.ser.ABSerializerModifier;

public class AfterburnerModule extends JacksonModule
    implements java.io.Serializable
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
     * {@link tools.jackson.databind.deser.bean.BeanDeserializer} or not.
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
