package tools.jackson.module.blackbird.internal;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdModule;

import static org.junit.jupiter.api.Assertions.*;

/**
 * On a runtime that generates codecs, the discovery provider must look like
 * an explicit registration to databind: same registration id (so discovery
 * plus an explicit registration installs Blackbird once) and the same module
 * name. (On Java 17 to 24 the ids differ, so that discovery cannot replace
 * an explicit registration, which must fail.)
 */
public class DiscoveredBlackbirdModuleTest
{
    @Test
    public void sameIdentityAsExplicitModule() {
        BlackbirdModule explicit = new BlackbirdModule();
        DiscoveredBlackbirdModule discovered = new DiscoveredBlackbirdModule();
        assertEquals(explicit.getRegistrationId(), discovered.getRegistrationId());
        assertEquals(explicit.getModuleName(), discovered.getModuleName());
    }

    @Test
    public void discoveryPlusExplicitRegistersOnce() {
        assertRegisteredOnce(JsonMapper.builder()
                .addModule(new DiscoveredBlackbirdModule())
                .addModule(new BlackbirdModule())
                .build());
        assertRegisteredOnce(JsonMapper.builder()
                .addModule(new BlackbirdModule())
                .addModule(new DiscoveredBlackbirdModule())
                .build());
    }

    private static void assertRegisteredOnce(ObjectMapper mapper) {
        List<JacksonModule> blackbirds = mapper.registeredModules().stream()
                .filter(m -> m instanceof BlackbirdModule)
                .toList();
        assertEquals(1, blackbirds.size(), "registered: " + blackbirds);
    }
}
