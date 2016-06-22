package com.fasterxml.jackson.module.paranamer;

import com.thoughtworks.paranamer.Paranamer;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.introspect.*;

/**
 * Stand-alone {@link AnnotationIntrospector} that defines functionality
 * to discover names of constructor (and factory method) parameters,
 * on top of default Jackson annotation processing.
 * It can be used as the replacement for vanilla
 * {@link JacksonAnnotationIntrospector}.
 */
public class ParanamerOnJacksonAnnotationIntrospector
    extends JacksonAnnotationIntrospector
{
    private static final long serialVersionUID = 1;

    /**
     * Wrapper used to encapsulate actual Paranamer call, to allow serialization
     * of this introspector
     */
    protected final SerializableParanamer _paranamer;

    public ParanamerOnJacksonAnnotationIntrospector() {
        this(new SerializableParanamer());
    }

    /**
     * @since 2.7.6
     */
    public ParanamerOnJacksonAnnotationIntrospector(SerializableParanamer pn) {
        _paranamer = pn;
    }

    /**
     * @deprecated since 2.7.6
     */
    @Deprecated
    public ParanamerOnJacksonAnnotationIntrospector(Paranamer pn) {
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

    @Override
    public String findImplicitPropertyName(AnnotatedMember param) {
        if (param instanceof AnnotatedParameter) {
            return _paranamer.findParameterName((AnnotatedParameter) param);
        }
        return null;
    }
}
