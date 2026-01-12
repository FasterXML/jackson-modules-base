package com.fasterxml.jackson.module.afterburner.deser;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;
//import com.fasterxml.jackson.module.afterburner.util.failure.JacksonTestFailureExpected;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AfterburnerGenericInterface314Test extends AfterburnerTestBase
{
    interface A314<T> {
        T getBody();
    }

    static class MD314 implements A314<String> {
        private String body;

        public MD314(String b) {
            body = b;
        }

        @Override
        public String getBody() {
            return body;
        }
    }

    //@JacksonTestFailureExpected
    @Test
    public void testMapperAfterUse() throws Exception
    {
        final ObjectMapper mapper = newObjectMapper();
        String json = mapper.writeValueAsString(new MD314("test"));
        assertEquals("{\"body\":\"test\"}", json);
    }
}
