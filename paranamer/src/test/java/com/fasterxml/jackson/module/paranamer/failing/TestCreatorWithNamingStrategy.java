/*
* Copyright (c) FasterXML, LLC.
* Licensed under the Apache (Software) License, version 2.0.
*/

package com.fasterxml.jackson.module.paranamer.failing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.module.paranamer.ParanamerModule;
import com.fasterxml.jackson.module.paranamer.ParanamerTestBase;

public class TestCreatorWithNamingStrategy
    extends ParanamerTestBase
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
            .setPropertyNamingStrategy(PropertyNamingStrategy.UPPER_CAMEL_CASE);

    public void testStaticStringCreator() throws Exception
    {
        StaticStringCreatorBean bean = MAPPER.readValue("\"42|NotMyRealName\"", StaticStringCreatorBean.class);
        assertEquals(42, bean.myAge);
        assertEquals("NotMyRealName", bean.myName);
    }
}
