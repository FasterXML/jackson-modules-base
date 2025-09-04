package com.fasterxml.jackson.module.blackbird;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for [Issue#21]
 */
public class TestSealedPackages extends BlackbirdTestBase
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
