package tools.jackson.module.blackbird;

import java.io.*;
import java.lang.invoke.MethodHandles;

import org.junit.jupiter.api.Test;

import tools.jackson.core.Version;
import tools.jackson.core.Versioned;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify that version information is properly accessible
 */
public class TestVersions extends BlackbirdTestBase
{
    @Test
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
        assertFalse(v.isUnknownVersion(), "Should find version information (got "+v+")");
        Version exp = PackageVersion.VERSION;
        assertEquals(exp.toFullString(), v.toFullString());
        assertEquals(exp, v);
    }
}

