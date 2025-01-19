package tools.jackson.module.jakarta.xmlbind.types;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.jakarta.xmlbind.ModuleTestBase;

import static org.junit.jupiter.api.Assertions.*;

// [jaxb-annotations#63]
public class OptionalTypeRefinementTest extends ModuleTestBase
{
    static class Stuff63 {
        public AtomicReference<String> value = new AtomicReference<String>("abc");
    }

    @Test
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
