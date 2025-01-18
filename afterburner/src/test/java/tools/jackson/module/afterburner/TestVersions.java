package tools.jackson.module.afterburner;

import java.io.*;

import org.junit.jupiter.api.Test;

import tools.jackson.core.Version;
import tools.jackson.core.Versioned;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests to verify that version information is properly accessible
 */
public class TestVersions extends AfterburnerTestBase
{
    @Test
    public void testMapperVersions() throws IOException
    {
        AfterburnerModule module = new AfterburnerModule();
        assertVersion(module);
    }

    private void assertVersion(Versioned vers)
    {
        Version v = vers.version();
        assertFalse(v.isUnknownVersion(),
                "Should find version information (got "+v+")");
        Version exp = PackageVersion.VERSION;
        assertEquals(exp.toFullString(), v.toFullString());
        assertEquals(exp, v);
    }
}

