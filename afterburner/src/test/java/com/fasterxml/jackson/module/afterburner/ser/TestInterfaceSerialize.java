package com.fasterxml.jackson.module.afterburner.ser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

public class TestInterfaceSerialize extends AfterburnerTestBase
{
    public interface Wat
    {
        String getFoo();
    }

    public void testInterfaceSerialize() throws Exception
    {
        ObjectMapper mapper = newAfterburnerMapper();

        Wat wat = new Wat() {
            @Override
            public String getFoo() {
                return "bar";
            }
        };

        // Causes IncompatibleClassChangeError
        assertEquals("{\"foo\":\"bar\"}", mapper.writerFor(Wat.class).writeValueAsString(wat));
    }
}
