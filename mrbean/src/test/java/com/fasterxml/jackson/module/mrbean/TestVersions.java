/*
* Copyright (c) FasterXML, LLC.
* Licensed to the Apache Software Foundation (ASF)
* under one or more contributor license agreements;
* and to You under the Apache License, Version 2.0.
*/

package com.fasterxml.jackson.module.mrbean;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.Versioned;

/**
 * Tests to verify proper version access.
 */
public class TestVersions extends BaseTest
{
    public void testMapperVersions()
    {
        assertVersion(new AbstractTypeMaterializer());
        assertVersion(new MrBeanModule());
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */
    
    private void assertVersion(Versioned vers)
    {
        final Version v = vers.version();
        assertFalse("Should find version information (got "+v+")", v.isUnknownVersion());
        assertEquals(PackageVersion.VERSION, v);
    }
}

