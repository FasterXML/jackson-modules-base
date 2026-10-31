package tools.jackson.module.blackbird;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JacksonModule;
import tools.jackson.module.blackbird.internal.DiscoveredBlackbirdModule;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ServiceLoader;

public class ModuleSPIMetadataTest
    extends BlackbirdTestBase
{
    @Test
    void testModuleSPIMetadata() {
        ServiceLoader<JacksonModule> loader
            = ServiceLoader.load(JacksonModule.class);
        assertTrue(loader.iterator().hasNext(),
                "Expected at least one `Module` implementation to be found via `ServiceLoader`");
        // Discovery instantiates the dedicated provider subclass (see
        // DiscoveredBlackbirdModule), not BlackbirdModule itself.
        final String exp = DiscoveredBlackbirdModule.class.getName();
        int count = 0;

        try {
            for (JacksonModule service : loader) {
                ++count;
                if (service.getClass().getName().equals(exp)) {
                    return;
                }
            }
        } catch (Throwable t) {
            fail("Expected to find `"+exp+"` Module (found "+count+" so far), problem: "+t);
        }
        fail("Expected to find `"+exp+"` Module (found "+count+" others)");
        assertEquals(1, count);
    }
}
