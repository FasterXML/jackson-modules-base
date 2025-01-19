package com.fasterxml.jackson.module.paranamer;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import static org.junit.jupiter.api.Assertions.*;

public class TestCreatorWithNamingStrategy
    extends ModuleTestBase
{
    static class CreatorBean
    {
        protected String myName;
        protected int myAge;

        @JsonCreator
        public CreatorBean(int myAge, String myName)
        {
            this.myName = myName;
            this.myAge = myAge;
        }
    }

    private final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new ParanamerModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE);

    @Test
    public void testSimpleConstructor() throws Exception
    {
        CreatorBean bean = MAPPER.readValue("{ \"MyAge\" : 42,  \"MyName\" : \"NotMyRealName\" }", CreatorBean.class);
        assertEquals(42, bean.myAge);
        assertEquals("NotMyRealName", bean.myName);
    }
}
