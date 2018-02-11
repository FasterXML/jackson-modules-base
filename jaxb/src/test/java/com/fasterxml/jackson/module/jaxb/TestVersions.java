package com.fasterxml.jackson.module.jaxb;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.Versioned;

import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

public class TestVersions extends BaseJaxbTest
{
    public void testVersions()
    {
        assertVersion(new JaxbAnnotationIntrospector());
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

