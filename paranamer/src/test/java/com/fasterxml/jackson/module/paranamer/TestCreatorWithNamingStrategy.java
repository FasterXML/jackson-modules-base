package com.fasterxml.jackson.module.paranamer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

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
            .setPropertyNamingStrategy(PropertyNamingStrategy.UPPER_CAMEL_CASE);

    public void testSimpleConstructor() throws Exception
    {
        CreatorBean bean = MAPPER.readValue("{ \"MyAge\" : 42,  \"MyName\" : \"NotMyRealName\" }", CreatorBean.class);
        assertEquals(42, bean.myAge);
        assertEquals("NotMyRealName", bean.myName);
    }
}