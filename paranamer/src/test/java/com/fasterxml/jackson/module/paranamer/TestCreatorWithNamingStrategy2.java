package com.fasterxml.jackson.module.paranamer;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import static org.junit.jupiter.api.Assertions.*;

public class TestCreatorWithNamingStrategy2
    extends ModuleTestBase
{
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

    private final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new ParanamerModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE);

    @Test
    public void testStaticStringCreator() throws Exception
    {
        StaticStringCreatorBean bean = MAPPER.readValue("\"42|NotMyRealName\"", StaticStringCreatorBean.class);
        assertEquals(42, bean.myAge);
        assertEquals("NotMyRealName", bean.myName);
    }
}
