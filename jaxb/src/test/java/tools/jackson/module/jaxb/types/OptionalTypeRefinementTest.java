package tools.jackson.module.jaxb.types;

import java.util.concurrent.atomic.AtomicReference;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.jaxb.BaseJaxbTest;

// [jaxb-annotations#63]
public class OptionalTypeRefinementTest extends BaseJaxbTest
{
    static class Stuff63 {
        public AtomicReference<String> value = new AtomicReference<String>("abc");
    }

    public void testWithReferenceType() throws Exception
    {
        final ObjectMapper mapper = getJaxbMapper();

        String json = mapper.writeValueAsString(new Stuff63());
        assertEquals("{\"value\":\"abc\"}", json);

        Stuff63 result = mapper.readValue("{\"value\":\"xyz\"}",
                Stuff63.class);
        assertNotNull(result);
        assertNotNull(result.value);
        assertEquals("xyz", result.value.get());
    }
}
