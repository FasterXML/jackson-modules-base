package com.fasterxml.jackson.module.paranamer;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;

import com.fasterxml.jackson.databind.introspect.AnnotatedParameter;
import com.thoughtworks.paranamer.BytecodeReadingParanamer;
import com.thoughtworks.paranamer.CachingParanamer;
import com.thoughtworks.paranamer.Paranamer;

/**
 * Simple wrapper used to hide the fact that paranamer accessor itself if not JDK serializable
 * in a way to keep actual <code>ObjectMapper</code> / <code>ObjectReader</code> serializable.
 */
public class SerializableParanamer
    implements java.io.Serializable
{
    private static final long serialVersionUID = 1L;

    protected transient Paranamer _paranamer;

    public SerializableParanamer() {
        this(null);
    }

    public SerializableParanamer(Paranamer paranamer) {
        if (paranamer == null) {
            paranamer = defaultParanamer();
        }
        _paranamer = paranamer;
    }

    /**
     * Overridable method in case someone really wants to sub-class this implementation.
     */
    protected Paranamer defaultParanamer() {
        return new CachingParanamer(new BytecodeReadingParanamer());
    }

    /*
    /**********************************************************
    /* Public API
    /**********************************************************
     */

    public String findParameterName(AnnotatedParameter param)
    {
        int index = param.getIndex();
        AnnotatedElement ctor = param.getOwner().getAnnotated();
        String[] names = _paranamer.lookupParameterNames((AccessibleObject) ctor, false);
        if (names != null && index < names.length) {
            return names[index];
        }
        return null;
    }

    /*
    /**********************************************************
    /* JDK serialization handling
    /**********************************************************
     */

    Object readResolve() {
        _paranamer = defaultParanamer();
        return this;
    }
}
