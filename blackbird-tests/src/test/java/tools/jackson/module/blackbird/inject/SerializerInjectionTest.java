package tools.jackson.module.blackbird.inject;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

// Verifies Blackbird's serializer codec generation engages end-to-end on the
// classpath and produces output identical to stock databind, for a getter POJO
// and for a public-field POJO (whose values are read through a generated
// getfield).
public class SerializerInjectionTest extends BlackbirdInjectionTestBase
{
    public static class GetterSerPojo {
        private int intProp;
        private long longProp;
        private boolean boolProp;
        private String stringProp;
        private java.util.List<String> objectProp;

        public GetterSerPojo() { }

        public GetterSerPojo(int i, long l, boolean b, String s, java.util.List<String> o) {
            intProp = i; longProp = l; boolProp = b; stringProp = s; objectProp = o;
        }

        public int getIntProp() { return intProp; }
        public long getLongProp() { return longProp; }
        public boolean isBoolProp() { return boolProp; }
        public String getStringProp() { return stringProp; }
        public java.util.List<String> getObjectProp() { return objectProp; }
    }

    public static class FieldSerPojo {
        public int value;

        public FieldSerPojo() { }
        public FieldSerPojo(int v) { this.value = v; }
    }

    @Test
    public void testGetterPojoEngagesGeneratedWriter() throws Exception
    {
        Harness h = newHarness();
        GetterSerPojo pojo = new GetterSerPojo(1, 2L, true, "x", java.util.Arrays.asList("a", "b"));

        String json = h.mapper.writeValueAsString(pojo);
        assertEquals(new JsonMapper().writeValueAsString(pojo), json,
                "generated writer output differs from stock databind");

        assertTrue(isBlackbirdWriter(h.serFor(GetterSerPojo.class)),
                "GetterSerPojo did not engage a Blackbird writer codec: "
                        + h.serFor(GetterSerPojo.class).getClass().getName());
    }

    @Test
    public void testFieldBackedWriterEngagesCodec() throws Exception
    {
        Harness h = newHarness();
        FieldSerPojo pojo = new FieldSerPojo(42);

        String json = h.mapper.writeValueAsString(pojo);
        assertEquals(new JsonMapper().writeValueAsString(pojo), json,
                "field-backed output differs from stock databind");

        assertTrue(isBlackbirdWriter(h.serFor(FieldSerPojo.class)),
                "FieldSerPojo did not engage a Blackbird writer codec: "
                        + h.serFor(FieldSerPojo.class).getClass().getName());
    }
}
