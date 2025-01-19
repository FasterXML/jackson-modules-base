package tools.jackson.module.mrbean;

import org.junit.jupiter.api.Test;

import tools.jackson.core.Version;
import tools.jackson.core.Versioned;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify proper version access.
 */
public class TestVersions extends BaseTest
{
    @Test
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
        assertFalse(v.isUnknownVersion(), "Should find version information (got "+v+")");
        assertEquals(PackageVersion.VERSION, v);
    }
}

