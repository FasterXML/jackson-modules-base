package tools.jackson.module.jakarta.xmlbind.introspect;

import org.junit.jupiter.api.Test;

import jakarta.xml.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.*;
import tools.jackson.module.jakarta.xmlbind.ModuleTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class TestXmlValue extends ModuleTestBase
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
    @Test
    public void testXmlValueDefault() throws Exception
    {
        ObjectMapper mapper = getJaxbAndJacksonMapper();
        // default is 'value'
        assertEquals("{\"value\":13}", mapper.writeValueAsString(new WithXmlValueNoOverride()));
    }

    // For [jaxb-annotations#30]
    @Test
    public void testXmlValueOverride() throws Exception
    {
        ObjectMapper mapper = getJaxbAndJacksonMapper();
        // default is 'value'
        assertEquals("{\"number\":13}", mapper.writeValueAsString(new WithXmlValueAndOverride()));
    }

    // For [jaxb-annotations#31]
    @Test
    public void testXmlValueDefault2() throws Exception
    {
        ObjectMapper mapper = getJaxbAndJacksonMapper();
        
        Query q2 = new Query();
        q2.query = "foo";
        
        // default is 'value'
        Query q = mapper.readValue("{\"value\":\"some stuff\"}", Query.class);
        assertEquals("some stuff", q.getQuery());
    }
}
