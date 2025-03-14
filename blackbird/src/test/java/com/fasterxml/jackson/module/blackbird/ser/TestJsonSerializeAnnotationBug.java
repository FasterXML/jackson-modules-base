package com.fasterxml.jackson.module.blackbird.ser;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import static org.junit.jupiter.api.Assertions.*;

public class TestJsonSerializeAnnotationBug
    extends com.fasterxml.jackson.module.blackbird.BlackbirdTestBase
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

    @Test
    public void testAfterburnerModule() throws Exception
    {
        ObjectMapper mapper = newObjectMapper();

        String value = mapper.writeValueAsString(new TestObjectWithJsonSerialize(new BigDecimal("870.04")));
        assertNotNull(value);
    }
}
