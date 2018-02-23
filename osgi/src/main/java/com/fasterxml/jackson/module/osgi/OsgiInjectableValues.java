package com.fasterxml.jackson.module.osgi;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.InjectableValues;

/**
 * Injects OSGI services in deserialized objects
 *<br>
 * Use the {@link com.fasterxml.jackson.annotation.JacksonInject} in the constructor parameters or the class members ask for injecting a matching OSGI services.
 * Use the {@link com.fasterxml.jackson.annotation.JacksonInject#value()} to specify an OSGI filter to select more accurately the OSGI services.
 * Null is injected when no matching OSGI service is registered.
 */
public class OsgiInjectableValues extends InjectableValues
{
    private final BundleContext bundleContext;

    public OsgiInjectableValues(BundleContext bundleContext)
    {
        this.bundleContext = bundleContext;
    }

    @Override
    public InjectableValues snapshot() {
        // 23-Feb-2018, tatu: Not sure if and how this could work really...
        return this;
    }
    
    @Override
    public Object findInjectableValue(Object valueId,
            DeserializationContext ctxt, BeanProperty forProperty, Object beanInstance)
    {
        return findService(serviceType(forProperty), serviceFilter(valueId));
    }

    private Object findService(String type, String filter)
    {
        try
        {
            ServiceReference<?>[] srs = bundleContext.getServiceReferences(type, filter);
            if (srs == null || srs.length == 0) {
                return null;
            }
            return bundleContext.getService(srs[0]);
        } catch (InvalidSyntaxException e) {
            // this will never happen as the filter was checked before
            throw new RuntimeException(e);
        }
    }

    private static String serviceType(BeanProperty forProperty)
    {
        return forProperty.getType().toCanonical();
    }

    private String serviceFilter(Object valueId)
    {
        try {
            return bundleContext.createFilter(valueId.toString()).toString();
        } catch (InvalidSyntaxException e) {
            return null;
        }
    }

}
