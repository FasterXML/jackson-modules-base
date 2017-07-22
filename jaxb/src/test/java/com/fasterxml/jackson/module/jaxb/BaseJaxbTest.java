package com.fasterxml.jackson.module.jaxb;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

public abstract class BaseJaxbTest
    extends junit.framework.TestCase
{
    protected BaseJaxbTest() { }
    
    /*
    /**********************************************************************
    /* Factory methods
    /**********************************************************************
     */

    protected ObjectMapper getJaxbMapper()
    {
        ObjectMapper mapper = new ObjectMapper();
        AnnotationIntrospector intr = new JaxbAnnotationIntrospector(mapper.getTypeFactory());
        mapper.setAnnotationIntrospector(intr);
        return mapper;
    }

    protected ObjectMapper getJaxbAndJacksonMapper()
    {
        ObjectMapper mapper = new ObjectMapper();
        AnnotationIntrospector intr = new AnnotationIntrospectorPair(new JaxbAnnotationIntrospector(
                mapper.getTypeFactory()), new JacksonAnnotationIntrospector());
        mapper.setAnnotationIntrospector(intr);
        return mapper;
    }

    protected ObjectMapper getJacksonAndJaxbMapper()
    {
        ObjectMapper mapper = new ObjectMapper();
        AnnotationIntrospector intr = new AnnotationIntrospectorPair(new JacksonAnnotationIntrospector(),
                new JaxbAnnotationIntrospector(mapper.getTypeFactory()) );
        mapper.setAnnotationIntrospector(intr);
        return mapper;
    }
    
    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    @SuppressWarnings("unchecked")
    protected Map<String,Object> writeAndMap(ObjectMapper m, Object value)
        throws IOException
    {
        String str = m.writeValueAsString(value);
        return (Map<String,Object>) m.readValue(str, Map.class);
    }

    protected Map<String,Object> writeAndMap(Object value)
        throws IOException
    {
        return writeAndMap(new ObjectMapper(), value);
    }

    protected String serializeAsString(ObjectMapper m, Object value)
        throws IOException
    {
        return m.writeValueAsString(value);
    }

    protected String serializeAsString(Object value)
        throws IOException
    {
        return serializeAsString(new ObjectMapper(), value);
    }
}
