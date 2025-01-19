package tools.jackson.module.blackbird.roundtrip;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleRoundTripTest extends BlackbirdTestBase
{
    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    private final ObjectMapper MAPPER = newObjectMapper();

    // Test that would fail with problems wrt "ultra-optimized" bean serializer/deserializer
    // (with max 6 properties limit)
    public void testPojoWith6Fiels() throws Exception
    {
        String json = MAPPER.writeValueAsString(new Pojo6());
        assertEquals(a2q("{'a':13,'b':'foo','c':true,'d':-13117,'e':0.25,'f':[1,2,3]}"),
                json);

        Pojo6 result = MAPPER.readValue(aposToQuotes(
                "{'a':256,'b':'bar','c':false,'d':1234567890,'e':-0.5,'f':[2,6]}"
                ), Pojo6.class);
        assertEquals(256, result.a);
        assertEquals("bar", result.getB());
        assertEquals(false, result.c);
        assertEquals(1234567890L, result.d);
        assertEquals(-0.5, result.getE());
        assertArrayEquals(new int[] { 2, 6 }, result.f);
    }
}
