package com.fasterxml.jackson.module.jaxb;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.Versioned;

import com.fasterxml.jackson.databind.type.TypeFactory;

import static org.junit.jupiter.api.Assertions.*;

public class TestVersions extends BaseJaxbTest
{
    @Test
    public void testVersions()
    {
        assertVersion(new JaxbAnnotationIntrospector(TypeFactory.defaultInstance()));
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */
    
    private void assertVersion(Versioned vers)
    {
        Version v = vers.version();
        assertFalse(v.isUnknownVersion(), "Should find version information (got "+v+")");
        Version exp = PackageVersion.VERSION;
        assertEquals(exp.toFullString(), v.toFullString());
        assertEquals(exp, v);
    }
}

