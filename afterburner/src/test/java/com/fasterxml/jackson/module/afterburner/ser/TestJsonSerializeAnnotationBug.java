package com.fasterxml.jackson.module.afterburner.ser;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public class TestJsonSerializeAnnotationBug
    extends com.fasterxml.jackson.module.afterburner.AfterburnerTestBase
{
    public static class TestObjectWithJsonSerialize {
        @JsonSerialize(using = ToStringSerializer.class)
        private final BigDecimal amount;

        @JsonCreator
        public TestObjectWithJsonSerialize(@JsonProperty("amount") BigDecimal amount) {
            this.amount = amount;
        }

        @JsonSerialize(using = ToStringSerializer.class) @JsonProperty("amount")
        public BigDecimal getAmount() {
            return amount;
        }
    }

    public void testAfterburnerModule() throws Exception
    {
        ObjectMapper mapper = newAfterburnerMapper();

        String value = mapper.writeValueAsString(new TestObjectWithJsonSerialize(new BigDecimal("870.04")));
        assertNotNull(value);
    }
}
