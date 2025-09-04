package com.fasterxml.jackson.module.afterburner.deser.filter;

import java.beans.ConstructorProperties;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class IgnoreCreatorProp1317Test extends AfterburnerTestBase
{
    static class Testing {
        @JsonIgnore
        public String ignore;

        String notIgnore;

        public Testing() {}

        @ConstructorProperties({"ignore", "notIgnore"})
        public Testing(String ignore, String notIgnore) {
            super();
            this.ignore = ignore;
            this.notIgnore = notIgnore;
        }

        public String getIgnore() {
            return ignore;
        }

        public void setIgnore(String ignore) {
            this.ignore = ignore;
        }

        public String getNotIgnore() {
            return notIgnore;
        }

        public void setNotIgnore(String notIgnore) {
            this.notIgnore = notIgnore;
        }
    }

    private final ObjectMapper MAPPER = newAfterburnerMapper();

    @Test
    public void testThatJsonIgnoreWorksWithConstructorProperties() throws Exception {
        Testing testing = new Testing("shouldBeIgnored", "notIgnore");
        String json = MAPPER.writeValueAsString(testing);
//        System.out.println(json);
        assertFalse(json.contains("shouldBeIgnored"));
    }
}
