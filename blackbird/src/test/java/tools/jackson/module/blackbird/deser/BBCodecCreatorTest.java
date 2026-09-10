package tools.jackson.module.blackbird.deser;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

// The value instantiator's default creator is not always the no-arg
// constructor: a no-arg @JsonCreator factory takes its place, and databind
// then constructs through the factory. Generated codecs must construct the
// same way, not through a direct `new` that bypasses the factory.
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

    @Test
    public void testRecordFactoryCreatorIsUsed() throws Exception {
        ObjectMapper vanilla = newVanillaJSONMapper();
        FactoryRec expected = vanilla.readValue("{\"name\":\"a\",\"count\":2}", FactoryRec.class);

        FactoryRec actual = newBlackbirdMapper().readValue("{\"name\":\"a\",\"count\":2}", FactoryRec.class);
        assertEquals(expected, actual,
                "record construction path differs from stock (creator is the factory)");
    }

    @Test
    public void testNoArgFactoryCreatorIsUsed() throws Exception {
        ObjectMapper vanilla = newVanillaJSONMapper();
        FactoryBean expected = vanilla.readValue("{\"name\":\"a\"}", FactoryBean.class);

        FactoryBean actual = newBlackbirdMapper().readValue("{\"name\":\"a\"}", FactoryBean.class);
        assertEquals("a", actual.name);
        assertEquals(expected.viaFactory, actual.viaFactory,
                "construction path differs from stock (default creator is the factory)");
    }
}
