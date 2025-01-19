package tools.jackson.module.jakarta.xmlbind;

import tools.jackson.core.Version;
import tools.jackson.core.Versioned;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestVersions extends ModuleTestBase
{
    @Test
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
        assertFalse(v.isUnknownVersion(), "Should find version information (got "+v+")");
        Version exp = PackageVersion.VERSION;
        assertEquals(exp.toFullString(), v.toFullString());
        assertEquals(exp, v);
    }
}

