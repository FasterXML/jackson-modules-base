package com.fasterxml.jackson.module.afterburner;

import java.io.*;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.Versioned;

/**
 * Tests to verify that version information is properly accessible
 */
public class TestVersions extends AfterburnerTestBase
{
    public void testMapperVersions() throws IOException
    {
        AfterburnerModule module = new AfterburnerModule();
        assertVersion(module);
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */
    
    private void assertVersion(Versioned vers)
    {
        Version v = vers.version();
        assertFalse("Should find version information (got "+v+")", v.isUnknownVersion());
        Version exp = PackageVersion.VERSION;
        assertEquals(exp.toFullString(), v.toFullString());
        assertEquals(exp, v);
    }
}

