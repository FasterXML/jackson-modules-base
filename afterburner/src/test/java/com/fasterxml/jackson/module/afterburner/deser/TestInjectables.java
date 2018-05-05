package com.fasterxml.jackson.module.afterburner.deser;

import com.fasterxml.jackson.annotation.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

public class TestInjectables extends AfterburnerTestBase
{
    static class IssueGH471Bean {

        protected final Object constructorInjected;
        protected final String constructorValue;

        @JacksonInject("field_injected") protected Object fieldInjected;
        @JsonProperty("field_value")     protected String fieldValue;

        protected Object methodInjected;
        protected String methodValue;

        public int x;
        
        @JsonCreator
        private IssueGH471Bean(@JacksonInject("constructor_injected") Object constructorInjected,
                               @JsonProperty("constructor_value") String constructorValue) {
            this.constructorInjected = constructorInjected;
            this.constructorValue = constructorValue;
        }

        @JacksonInject("method_injected")
        private void setMethodInjected(Object methodInjected) {
            this.methodInjected = methodInjected;
        }

        @JsonProperty("method_value")
        public void setMethodValue(String methodValue) {
            this.methodValue = methodValue;
        }
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    public void testIssueGH471() throws Exception
    {
        final Object constructorInjected = "constructorInjected";
        final Object methodInjected = "methodInjected";
        final Object fieldInjected = "fieldInjected";

        ObjectMapper mapper = afterburnerMapperBuilder()
                .injectableValues(new InjectableValues.Std()
                        .addValue("constructor_injected", constructorInjected)
                        .addValue("method_injected", methodInjected)
                        .addValue("field_injected", fieldInjected))
                .build();
        IssueGH471Bean bean = mapper.readValue("{\"x\":13,\"constructor_value\":\"constructor\",\"method_value\":\"method\",\"field_value\":\"field\"}",
                IssueGH471Bean.class);

        // Assert *SAME* instance
        assertSame(constructorInjected, bean.constructorInjected);
        assertSame(methodInjected, bean.methodInjected);
        assertSame(fieldInjected, bean.fieldInjected);

        // Check that basic properties still work (better safe than sorry)
        assertEquals("constructor", bean.constructorValue);
        assertEquals("method", bean.methodValue);
        assertEquals("field", bean.fieldValue);

        assertEquals(13, bean.x);
    }
}
