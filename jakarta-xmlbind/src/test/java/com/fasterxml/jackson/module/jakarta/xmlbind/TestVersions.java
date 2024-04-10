package com.fasterxml.jackson.module.jakarta.xmlbind;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.Versioned;

import com.fasterxml.jackson.databind.type.TypeFactory;

public class TestVersions extends ModuleTestBase
{
    public void testVersions()
    {
        assertVersion(new JakartaXmlBindAnnotationIntrospector(TypeFactory.defaultInstance()));
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

