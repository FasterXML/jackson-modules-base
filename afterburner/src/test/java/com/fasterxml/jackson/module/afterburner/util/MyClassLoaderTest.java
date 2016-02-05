package com.fasterxml.jackson.module.afterburner.util;

import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

public class MyClassLoaderTest extends AfterburnerTestBase
{
    public void testNameReplacement() throws Exception
    {
        byte[] input = "Something with FOO in it (but not just FO!): FOOFOO".getBytes("UTF-8");
        int count = MyClassLoader.replaceName(input, "FOO", "BAR");
        assertEquals(3, count);
        assertEquals("Something with BAR in it (but not just FO!): BARBAR", new String(input, "UTF-8"));
    }
}
