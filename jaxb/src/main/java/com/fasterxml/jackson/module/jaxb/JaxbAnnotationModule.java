package com.fasterxml.jackson.module.jaxb;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;

/**
 * Module that can be registered to add support for JAXB annotations.
 * It does basically equivalent of
 *<pre>
 *   objectMapper.setAnnotationIntrospector(...);
 *</pre>
 * with combination of {@link JaxbAnnotationIntrospector} and existing
 * default introspector(s) (if any), depending on configuration
 * (by default, JAXB annotations are used as {@link Priority#PRIMARY}
 * annotations).
 */
public class JaxbAnnotationModule extends Module
{
    /**
     * Enumeration that defines how we use JAXB Annotations: either
     * as "primary" annotations (before any other already configured
     * introspector -- most likely default JacksonAnnotationIntrospector) or
     * as "secondary" annotations (after any other already configured
     * introspector(s)).
     *<p>
     * Default choice is <b>PRIMARY</b>
     *<p>
     * Note that if you want to use JAXB annotations as the only annotations,
     * you must directly set annotation introspector by calling 
     * {@link com.fasterxml.jackson.databind.ObjectMapper#setAnnotationIntrospector}.
     */
    public enum Priority {
        PRIMARY, SECONDARY;
    }
    
    /**
     * Priority to use when registering annotation introspector: default
     * value is {@link Priority#PRIMARY}.
     */
    protected Priority _priority = Priority.PRIMARY;

    /**
     * If the introspector is explicitly set or passed, we'll hold on to that
     * until registration.
     */
    protected JaxbAnnotationIntrospector _introspector;

    /**
     * Value to pass to
     * {@link JaxbAnnotationIntrospector#setNonNillableInclusion}
     * if defined and non-null.
     */
    protected JsonInclude.Include _nonNillableInclusion;

    /*
    /**********************************************************
    /* Life cycle
    /**********************************************************
     */

    public JaxbAnnotationModule() { }

    public JaxbAnnotationModule(JaxbAnnotationIntrospector intr) {
        _introspector = intr;
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
        JaxbAnnotationIntrospector intr = _introspector;
        if (intr == null) {
            intr = new JaxbAnnotationIntrospector();
            if (_nonNillableInclusion != null) {
                intr.setNonNillableInclusion(_nonNillableInclusion);
            }
        }
        switch (_priority) {
        case PRIMARY:
            context.insertAnnotationIntrospector(intr);
            break;
        case SECONDARY:
            context.appendAnnotationIntrospector(intr);
            break;
        }
    }

    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */
    
    /**
     * Method for defining whether JAXB annotations should be added
     * as primary or secondary annotations (compared to already registered
     * annotations).
     *<p>
     * NOTE: method MUST be called before registering the module -- calling
     * afterwards will not have any effect on previous registrations.
     */
    public JaxbAnnotationModule setPriority(Priority p) {
        _priority = p;
        return this;
    }
    
    public Priority getPriority() { return _priority; }

    public JaxbAnnotationModule setNonNillableInclusion(JsonInclude.Include incl) {
        _nonNillableInclusion = incl;
        if (_introspector != null) {
            _introspector.setNonNillableInclusion(incl);
        }
        return this;
    }

    public JsonInclude.Include getNonNillableInclusion() {
        return _nonNillableInclusion;
    }
}
