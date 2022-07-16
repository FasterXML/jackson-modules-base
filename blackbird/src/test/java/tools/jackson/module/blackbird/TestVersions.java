package tools.jackson.module.blackbird;

import java.io.*;
import java.lang.invoke.MethodHandles;

import com.fasterxml.jackson.module.blackbird.PackageVersion;

import tools.jackson.core.Version;
import tools.jackson.core.Versioned;
import tools.jackson.module.blackbird.BlackbirdModule;

/**
 * Tests to verify that version information is properly accessible
 */
public class TestVersions extends BlackbirdTestBase
{
    public void testMapperVersions() throws IOException
    {
        BlackbirdModule module = new BlackbirdModule(c -> MethodHandles.lookup());
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

