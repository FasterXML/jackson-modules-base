package com.fasterxml.jackson.module.jakarta.xmlbind;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.fasterxml.jackson.core.Version;

import com.fasterxml.jackson.databind.JacksonModule;

/**
 * Module that can be registered to add support for JAXB annotations.
 * It does basically equivalent of
 *<pre>
 *   objectMapper.setAnnotationIntrospector(...);
 *</pre>
 * with combination of {@link JakartaXmlBindAnnotationIntrospector} and existing
 * default introspector(s) (if any), depending on configuration
 * (by default, JAXB annotations are used as {@link Priority#PRIMARY}
 * annotations).
 */
public class JakartaXmlBindAnnotationModule
    extends JacksonModule
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
     * you must directly set annotation introspector by constructing
     * {@code ObjectMapper} via builder and assign {@link JakartaXmlBindAnnotationIntrospector}
     * as the only introspector (instead of inserting or appending).
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
     *
     * @since 2.7
     */
    protected JakartaXmlBindAnnotationIntrospector _introspector;

    /**
     * Value to pass to
     * {@link JakartaXmlBindAnnotationIntrospector#setNonNillableInclusion}
     * if defined and non-null.
     *
     * @since 2.7
     */
    protected JsonInclude.Include _nonNillableInclusion;

    /**
     * Value to pass to
     * {@link JakartaXmlBindAnnotationIntrospector#setNameUsedForXmlValue}
     * if introspector constructed by the module.
     *
     * @since 2.12
     */
    protected String _nameUsedForXmlValue = JakartaXmlBindAnnotationIntrospector.DEFAULT_NAME_FOR_XML_VALUE;

    /*
    /**********************************************************************
    /* Life cycle
    /**********************************************************************
     */

    public JakartaXmlBindAnnotationModule() { }

    /**
     * @since 2.7
     */
    public JakartaXmlBindAnnotationModule(JakartaXmlBindAnnotationIntrospector intr) {
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
        JakartaXmlBindAnnotationIntrospector intr = _introspector;
        if (intr == null) {
            intr = new JakartaXmlBindAnnotationIntrospector();
            if (_nonNillableInclusion != null) {
                intr.setNonNillableInclusion(_nonNillableInclusion);
            }
            intr.setNameUsedForXmlValue(_nameUsedForXmlValue);
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
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */
    
    /**
     * Method for defining whether JAXB annotations should be added
     * as primary or secondary annotations (compared to already registered
     * annotations).
     *<p>
     * NOTE: method MUST be called before registering the module -- calling
     * afterwards will not have any effect on previous registrations.
     */
    public JakartaXmlBindAnnotationModule setPriority(Priority p) {
        _priority = p;
        return this;
    }
    
    public Priority getPriority() { return _priority; }

    /**
     * @since 2.7
     */
    public JakartaXmlBindAnnotationModule setNonNillableInclusion(JsonInclude.Include incl) {
        _nonNillableInclusion = incl;
        if (_introspector != null) {
            // 13-Nov-2020, tatu: should we pass null "incl"?
            _introspector.setNonNillableInclusion(incl);
        }
        return this;
    }

    /**
     * @since 2.7
     */
    public JsonInclude.Include getNonNillableInclusion() {
        return _nonNillableInclusion;
    }

    /**
     * @since 2.12
     */
    public JakartaXmlBindAnnotationModule setNameUsedForXmlValue(String name) {
        _nameUsedForXmlValue = name;
        return this;
    }

    public String getNameUsedForXmlValue() {
        return _nameUsedForXmlValue;
    }
}
