package com.fasterxml.jackson.module.blackbird.deser.java8;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;

// for [modules-base#223]
public class DefaultMethods223Test extends BlackbirdTestBase
{
    interface Animal223 {
        public String getName();

        public void setName(String name);
        default public String getInfo() {
            return "";
        }

        // Problematic case:
        default public void setInfo(String info) { }
    }

    @JsonPropertyOrder({ "name", "info" })
    static class Cat223 implements Animal223 {
        String name;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName(String name) {
            this.name = name;
        }
    }

    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    private final ObjectMapper MAPPER = newObjectMapper();

    public void testSerializeViaDefault223() throws Exception
    {
        Cat223 cat = new Cat223();
        cat.setName("Molly");
        assertEquals(a2q("{'name':'Molly','info':''}"),
                MAPPER.writeValueAsString(cat));
    }

    public void testDeserializeViaDefault223() throws Exception
    {
        String json = a2q("{'name':'Emma','info':'xyz'}");
        Cat223 cat = MAPPER.readValue(json, Cat223.class);
        assertEquals("Emma", cat.getName());
        assertEquals("", cat.getInfo());
    }
}
