package tools.jackson.module.mrbean;

import java.util.Collections;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestMapStringObjectDeserialization
    extends BaseTest
{
    /**
     * Test simple Map deserialization works.
     */
    public void testMapWithMrbean() throws Exception
    {
        runTest(newMrBeanMapper());
    }

    /**
     * Test simple Map deserialization works.
     */
    public void testMapWithoutMrbean() throws Exception
    {
        runTest(newPlainJsonMapper());
    }

    private void runTest(ObjectMapper mapper)
    {
        Map<String, Object> map = mapper.readValue("{\"test\":3 }",
                new TypeReference<Map<String, Object>>() {});
        assertEquals(Collections.singletonMap("test", 3), map);
    }
}
