package com.fasterxml.jackson.module.jaxb.introspect;

import java.io.IOException;

import javax.xml.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

public class TestXmlValue extends BaseJaxbTest
{
    static class WithXmlValueNoOverride
    {
        @XmlValue
        public int getFoobar() {
            return 13;
        }
    }

    static class WithXmlValueAndOverride
    {
        @XmlValue
        @JsonProperty("number")
        public int getFoobar() {
            return 13;
        }
    }
    
    // [jaxb-annotations#31]
    static class Query {
        @XmlValue
        protected String query;

        public String getQuery() {
            return query;
        }

        public void setQuery(final String pQuery) {
            query = pQuery;
        }
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    // For [jaxb-annotations#30]
    public void testXmlValueDefault() throws IOException
    {
        ObjectMapper mapper = getJaxbAndJacksonMapper();
        // default is 'value'
        assertEquals("{\"value\":13}", mapper.writeValueAsString(new WithXmlValueNoOverride()));
    }

    // For [jaxb-annotations#30]
    public void testXmlValueOverride() throws IOException
    {
        ObjectMapper mapper = getJaxbAndJacksonMapper();
        // default is 'value'
        assertEquals("{\"number\":13}", mapper.writeValueAsString(new WithXmlValueAndOverride()));
    }

    // For [jaxb-annotations#31]
    public void testXmlValueDefault2() throws IOException
    {
        ObjectMapper mapper = getJaxbAndJacksonMapper();
        
        Query q2 = new Query();
        q2.query = "foo";
        
        // default is 'value'
        Query q = mapper.readValue("{\"value\":\"some stuff\"}", Query.class);
        assertEquals("some stuff", q.getQuery());
    }
}
