package com.fasterxml.jackson.module.paranamer;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedParameter;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import com.thoughtworks.paranamer.*;

/**
 * Stand-alone {@link AnnotationIntrospector} that defines functionality
 * to discover names of constructor (and factory method) parameters,
 * without using any defaulting.
 * Can be used as chainable add-on introspector.
 */
public class ParanamerAnnotationIntrospector
    extends NopAnnotationIntrospector
{
    private static final long serialVersionUID = 1;

    /**
     * Wrapper used to encapsulate actual Paranamer call, to allow serialization
     * of this introspector
     */
    protected final SerializableParanamer _paranamer;

    public ParanamerAnnotationIntrospector() {
        this(new SerializableParanamer());
    }

    /**
     * @since 2.7.6
     */
    public ParanamerAnnotationIntrospector(SerializableParanamer pn) {
        _paranamer = pn;
    }

    public ParanamerAnnotationIntrospector(Paranamer pn) {
        this(new SerializableParanamer(pn));
    }

    @Override
    public PropertyName findNameForDeserialization(Annotated a)
    {
        /* 14-Apr-2014, tatu: Important -- we should NOT introspect name here,
         *   since we are not using annotations; instead it needs to be done
         *   in {@link #findParameterSourceName(AnnotatedParameter)}.
         */
        /*
        PropertyName name = super.findNameForDeserialization(a);
        if (name == null) {
            if (a instanceof AnnotatedParameter) {
                String rawName _paranamer.findParameterName((AnnotatedParameter) a);
                if (rawName != null) {
                    return new PropertyName(rawName);
                }
            }
        }
        */
        return null;
    }

    // since 2.4
    @Override
    public String findImplicitPropertyName(AnnotatedMember param) {
        if (param instanceof AnnotatedParameter) {
            return _paranamer.findParameterName((AnnotatedParameter) param);
        }
        return null;
    }
}
