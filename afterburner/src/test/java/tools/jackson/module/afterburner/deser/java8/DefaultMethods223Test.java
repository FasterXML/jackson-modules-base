package tools.jackson.module.afterburner.deser.java8;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import tools.jackson.databind.ObjectMapper;

import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

// for [modules-base#223]
public class DefaultMethods223Test extends AfterburnerTestBase
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

    private final ObjectMapper MAPPER = newAfterburnerMapper();

    @Test
    public void testSerializeViaDefault223() throws Exception
    {
        Cat223 cat = new Cat223();
        cat.setName("Molly");
        assertEquals(a2q("{'name':'Molly','info':''}"),
                MAPPER.writeValueAsString(cat));
    }

    @Test
    public void testDeserializeViaDefault223() throws Exception
    {
        String json = a2q("{'name':'Emma','info':'xyz'}");
        Cat223 cat = MAPPER.readValue(json, Cat223.class);
        assertEquals("Emma", cat.getName());
        assertEquals("", cat.getInfo());
    }
}
