package tools.jackson.module.blackbird.deser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

// The value instantiator's default creator is not always the no-arg
// constructor: a no-arg @JsonCreator factory takes its place, and databind
// then constructs through the factory. Generated POJO codecs construct
// through the stock instantiator in that case (and still engage); record
// codecs construct through the canonical constructor only, so a record with
// a factory creator stays on the stock path.
public class BBCodecCreatorTest extends BlackbirdTestBase
{
    public static class FactoryBean {
        public String name;
        public boolean viaFactory;

        public FactoryBean() { }

        @JsonCreator
        public static FactoryBean create() {
            FactoryBean bean = new FactoryBean();
            bean.viaFactory = true;
            return bean;
        }
    }

    public record FactoryRec(String name, int count) {
        @JsonCreator
        public static FactoryRec create(@JsonProperty("name") String name,
                @JsonProperty("count") int count) {
            return new FactoryRec(name + "!", count);
        }
    }

    // Canonical constructor is varargs: the generated codec must pass the
    // array through, not wrap it as a single element
    public record VarargsRec(String name, String... tags) { }

    private final ObjectMapper vanilla = newVanillaJSONMapper();

    @Test
    public void testRecordFactoryCreatorIsUsed() throws Exception {
        FactoryRec expected = vanilla.readValue("{\"name\":\"a\",\"count\":2}", FactoryRec.class);

        FactoryRec actual = newBlackbirdMapper().readValue("{\"name\":\"a\",\"count\":2}", FactoryRec.class);
        assertEquals(expected, actual,
                "record construction path differs from stock (creator is the factory)");
    }

    @Test
    public void testNoArgFactoryCreatorIsUsedAndEngages() throws Exception {
        FactoryBean expected = vanilla.readValue("{\"name\":\"a\"}", FactoryBean.class);

        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        FactoryBean actual = mapper.readValue("{\"name\":\"a\"}", FactoryBean.class);
        assertTrue(hasGeneratedCodec(seen.get(FactoryBean.class)),
                "factory-creator bean did not engage a codec");
        assertEquals("a", actual.name);
        assertEquals(expected.viaFactory, actual.viaFactory,
                "construction path differs from stock (default creator is the factory)");
        assertTrue(actual.viaFactory);
    }

    @Test
    public void testVarargsRecordConstructor() throws Exception {
        final String json = "{\"name\":\"a\",\"tags\":[\"x\",\"y\"]}";
        VarargsRec expected = vanilla.readValue(json, VarargsRec.class);

        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        VarargsRec actual = mapper.readValue(json, VarargsRec.class);
        assertTrue(hasGeneratedCodec(seen.get(VarargsRec.class)),
                "varargs record did not engage a codec");
        assertEquals(expected.name(), actual.name());
        assertArrayEquals(expected.tags(), actual.tags());
        assertArrayEquals(new String[] { "x", "y" }, actual.tags());
    }
}
