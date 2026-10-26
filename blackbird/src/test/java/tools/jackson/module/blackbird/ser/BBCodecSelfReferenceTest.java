package tools.jackson.module.blackbird.ser;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A child property whose value is the bean itself goes to the stock
 * self-reference handling, but its getter must still run once, as in stock.
 */
public class BBCodecSelfReferenceTest extends BlackbirdTestBase
{
    public static class Base {
        public int getB() { return 1; }
    }

    @JsonPropertyOrder({ "self", "calls", "b" })
    public static class Derived extends Base {
        private int calls;

        @JsonSerialize(typing = JsonSerialize.Typing.STATIC)
        public Base getSelf() {
            calls++;
            return this;
        }

        public int getCalls() { return calls; }
    }

    private static void assertMatchesVanilla(SerializationFeature disable,
            SerializationFeature enable) throws Exception {
        JsonMapper.Builder vb = JsonMapper.builder().disable(disable);
        JsonMapper.Builder mb = mapperBuilder().disable(disable);
        if (enable != null) {
            vb.enable(enable);
            mb.enable(enable);
        }
        ObjectMapper vanilla = vb.build();
        ObjectMapper module = mb.build();
        String expected = vanilla.writeValueAsString(new Derived());
        assertTrue(expected.contains("\"calls\":1"), expected);
        assertEquals(expected, module.writeValueAsString(new Derived()));
        assertTrue(assertGeneratedWriter(module, Derived.class).contains("self=CHILD"));
    }

    @Test
    public void testSelfReferenceReadsGetterOnce() throws Exception {
        assertMatchesVanilla(SerializationFeature.FAIL_ON_SELF_REFERENCES, null);
    }

    @Test
    public void testSelfReferenceAsNullReadsGetterOnce() throws Exception {
        assertMatchesVanilla(SerializationFeature.FAIL_ON_SELF_REFERENCES,
                SerializationFeature.WRITE_SELF_REFERENCES_AS_NULL);
    }

    @Test
    public void testSelfReferenceFailsLikeStock() throws Exception {
        Derived bean = new Derived();
        Exception e = assertThrows(Exception.class, () -> newObjectMapper().writeValueAsString(bean));
        Exception expected = assertThrows(Exception.class,
                () -> new JsonMapper().writeValueAsString(new Derived()));
        assertEquals(expected.getClass(), e.getClass());
        assertEquals(1, bean.getCalls());
    }
}
