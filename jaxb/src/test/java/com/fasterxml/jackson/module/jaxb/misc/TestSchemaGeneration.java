package com.fasterxml.jackson.module.jaxb.misc;

import javax.xml.bind.annotation.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.jsonschema.JsonSchema;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test(s) to see that JAXB annotations-based information is properly
 * accessible and used by JSON Schema generation
 * 
 * @author tatu
 */
@SuppressWarnings("deprecation")
public class TestSchemaGeneration extends BaseJaxbTest
{
    @XmlAccessorType(XmlAccessType.FIELD)
    protected static class Person {
        public String firstName;
        public String lastName;
    
        @XmlElement(type=Address.class)
        public IAddress address;
    }

    protected interface IAddress {     
        public String getCity();
        public void setCity(String city);
    }

    protected static class Address implements IAddress {
        private String city;
        private String state;
        
        @Override
        public String getCity() { return city; }
        @Override
        public void setCity(String city) { this.city = city; }

        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
    }

    /*
    /**********************************************************
    /* Tests
    /**********************************************************
     */
    
    /**
     * Test for [JACKSON-415]
     * 
     * @since 1.7
     */
    @Test
    public void testWithJaxb() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        JsonSchema jsonSchema = mapper.generateJsonSchema(Address.class);
        ObjectNode root = jsonSchema.getSchemaNode();
        // should find two properties ("city", "state"), not just one...
        JsonNode itemsNode = root.findValue("properties");
        assertNotNull(itemsNode.get("state"), "Missing 'state' field");
        assertNotNull(itemsNode.get("city"), "Missing 'city' field");
        assertEquals(2, itemsNode.size());
    }
}
