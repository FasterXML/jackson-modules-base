package com.fasterxml.jackson.module.afterburner.deser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

public class GenericPropertyDeserializationTest extends AfterburnerTestBase
{
    static abstract class AbstractMyClass<ID> {
        private ID id;

        AbstractMyClass() { }

        public ID getId() {
            return id;
        }

        public void setId(ID id) {
            this.id = id;
        }
    }

    public static class MyClass extends AbstractMyClass<String> {
        public MyClass() { }
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    final private ObjectMapper MAPPER = newAfterburnerMapper();

    public void testGenericIssue4() throws Exception
    {
        MyClass result = MAPPER.readValue("{\"id\":\"foo\"}", MyClass.class);
        assertEquals("foo", result.getId());
    }
}
