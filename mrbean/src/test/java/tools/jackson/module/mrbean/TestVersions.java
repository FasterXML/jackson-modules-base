package tools.jackson.module.mrbean;

import tools.jackson.core.Version;
import tools.jackson.core.Versioned;

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

