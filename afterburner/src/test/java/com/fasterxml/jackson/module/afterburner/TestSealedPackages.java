package com.fasterxml.jackson.module.afterburner;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for [Issue#21]
 */
public class TestSealedPackages extends AfterburnerTestBase
{
    private final ObjectMapper MAPPER = newObjectMapper();

    @Test
    public void testJavaStdDeserialization() throws Exception
    {
        String json = "{}";
        Exception e = MAPPER.readValue(json, Exception.class);
        assertNotNull(e);
    }

    @Test
    public void testJavaStdSerialization() throws Exception
    {
        String json = MAPPER.writeValueAsString(Thread.currentThread().getThreadGroup());
        assertNotNull(json);
    }
}
