package tools.jackson.module.afterburner.misc;

import java.util.Map;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.afterburner.AfterburnerTestBase;

public class PreventJDKTypeAccessTest extends AfterburnerTestBase
{
    private final ObjectMapper MAPPER = newObjectMapper();
    private final ObjectMapper VANILLA_MAPPER = newVanillaJSONMapper();

    public void testJDKThreadroundTrip() throws Exception
    {
        final Object input = Thread.currentThread();
        final String json1 = VANILLA_MAPPER.writeValueAsString(input);
        final String json2 = MAPPER.writeValueAsString(input);

        Map<?,?> map1 = VANILLA_MAPPER.readValue(json1, Map.class);
        Map<?,?> map2 = MAPPER.readValue(json2, Map.class);

        assertEquals(map1.keySet(), map2.keySet());
    }
}
