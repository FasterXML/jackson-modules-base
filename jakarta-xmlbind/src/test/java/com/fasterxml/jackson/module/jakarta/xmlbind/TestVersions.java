package com.fasterxml.jackson.module.jakarta.xmlbind;

import tools.jackson.core.Version;
import tools.jackson.core.Versioned;

public class TestVersions extends ModuleTestBase
{
    public void testVersions()
    {
        assertVersion(new JakartaXmlBindAnnotationIntrospector());
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
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

