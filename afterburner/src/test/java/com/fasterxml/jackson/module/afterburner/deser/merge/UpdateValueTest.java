package com.fasterxml.jackson.module.afterburner.deser.merge;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class UpdateValueTest extends AfterburnerTestBase
{
    static class Bean
    {
        private String a;
        private String b;

        @JsonCreator
        public Bean(@JsonProperty("a") String a, @JsonProperty("b") String b)
        {
            this.a = a;
            this.b = b;
        }

        String getA() {
            return a;
        }

        void setA(String a) {
            this.a = a;
        }

        String getB() {
            return b;
        }

        void setB(String b) {
            this.b = b;
        }
    }

    private final ObjectMapper MAPPER = newObjectMapper();
    
    // [databind#318] (and Scala module issue #83]
    @Test
    public void testValueUpdateWithCreator() throws Exception
    {
        Bean bean = new Bean("abc", "def");
        MAPPER.readerFor(Bean.class).withValueToUpdate(bean).readValue("{\"a\":\"ghi\",\"b\":\"jkl\"}");
        assertEquals("ghi", bean.getA());
        assertEquals("jkl", bean.getB());
    }

    @Test
    public void testValueUpdateOther() throws Exception
    {
        Bean bean = new Bean("abc", "def");
        ObjectReader r = MAPPER.readerFor(Bean.class).withValueToUpdate(bean);
        // but, changed our minds, no update
        r = r.withValueToUpdate(null);
        // should be safe to read regardless
        Bean result = r.readValue(aposToQuotes("{'a':'x'}"));
        assertNotNull(result);
    }
}
