/*
* Copyright (c) FasterXML, LLC.
* Licensed under the Apache (Software) License, version 2.0.
*/

package com.fasterxml.jackson.module.afterburner;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for [Issue#21]
 */
public class TestSealedPackages extends AfterburnerTestBase
{
    public void testJavaStdDeserialization() throws Exception
    {
        final ObjectMapper MAPPER = mapperWithModule();
        String json = "{}";
        Exception e = MAPPER.readValue(json, Exception.class);
        assertNotNull(e);
    }

    public void testJavaStdSerialization() throws Exception
    {
        final ObjectMapper MAPPER = mapperWithModule();
        String json = MAPPER.writeValueAsString(Thread.currentThread().getThreadGroup());
        assertNotNull(json);
    }
}
