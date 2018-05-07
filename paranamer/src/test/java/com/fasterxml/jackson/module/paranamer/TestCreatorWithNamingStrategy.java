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

    static class StaticStringCreatorBean
    {
        protected String myName;
        protected int myAge;

        public StaticStringCreatorBean(int myAge, String myName)
        {
            this.myName = myName;
            this.myAge = myAge;
        }

        @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
        public static StaticStringCreatorBean parse(String delimited)
        {
            String[] args = delimited.split("\\|");
            if (args.length != 2) {
                throw new IllegalArgumentException("Invalid string: " + delimited + ". Expected 'age|name'.");
            }
            int age = Integer.parseInt(args[0]);
            return new StaticStringCreatorBean(age, args[1]);
        }
    }
    
    private final ObjectMapper MAPPER = ObjectMapper.builder()
            .addModule(new ParanamerModule())
            .propertyNamingStrategy(PropertyNamingStrategy.UPPER_CAMEL_CASE)
            .build();

    public void testSimpleConstructor() throws Exception
    {
        CreatorBean bean = MAPPER.readValue("{ \"MyAge\" : 42,  \"MyName\" : \"NotMyRealName\" }", CreatorBean.class);
        assertEquals(42, bean.myAge);
        assertEquals("NotMyRealName", bean.myName);
    }

    public void testStaticStringCreator() throws Exception
    {
        StaticStringCreatorBean bean = MAPPER.readValue("\"42|NotMyRealName\"", StaticStringCreatorBean.class);
        assertEquals(42, bean.myAge);
        assertEquals("NotMyRealName", bean.myName);
    }
}