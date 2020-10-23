package com.fasterxml.jackson.module.blackbird.ser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;

public class TestInterfaceSerialize extends BlackbirdTestBase
{
    public interface Wat
    {
        String getFoo();
    }

    public void testInterfaceSerialize() throws Exception
    {
        ObjectMapper mapper = newObjectMapper();

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
