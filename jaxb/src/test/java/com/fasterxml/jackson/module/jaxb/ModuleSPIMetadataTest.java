package com.fasterxml.jackson.module.jaxb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ServiceLoader;

public class ModuleSPIMetadataTest
    extends BaseJaxbTest
{
    @Test
    void testModuleSPIMetadata() {
        ServiceLoader<com.fasterxml.jackson.databind.Module> loader
            = ServiceLoader.load(com.fasterxml.jackson.databind.Module.class);
        assertTrue(loader.iterator().hasNext(),
                "Expected at least one `Module` implementation to be found via `ServiceLoader`");
        final String exp = JaxbAnnotationModule.class.getName();
        int count = 0;

        try {
            for (com.fasterxml.jackson.databind.Module service : loader) {
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
