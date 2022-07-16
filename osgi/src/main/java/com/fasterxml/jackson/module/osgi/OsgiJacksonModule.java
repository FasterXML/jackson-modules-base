package com.fasterxml.jackson.module.osgi;

import org.osgi.framework.BundleContext;

import tools.jackson.core.Version;
import tools.jackson.databind.JacksonModule;

/**
 * Jackson Module to inject OSGI services in deserialized objects.
 * Note that registration will replace possibly existing default value injector
 * ({@link com.fasterxml.jackson.databind.InjectableValues}).
 * If you want a combination, you will need to add bridging functionality between
 * default implementation and {@link OsgiInjectableValues}.
 *
 * @see OsgiInjectableValues
 */
public class OsgiJacksonModule extends JacksonModule
{
    private final BundleContext bundleContext;

    public OsgiJacksonModule(BundleContext bundleContext)
    {
        this.bundleContext = bundleContext;
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
        context.overrideInjectableValues(v -> 
                new OsgiInjectableValues(bundleContext));
    }
}
