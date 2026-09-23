package tools.jackson.module.blackbird.deser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.module.blackbird.BlackbirdModule;
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

    private final ObjectMapper vanilla = newVanillaJSONMapper();

    private static ObjectMapper capturingMapper(Map<Class<?>, ValueDeserializer<?>> seen) {
        SimpleModule capture = new SimpleModule("capture") {
            private static final long serialVersionUID = 1L;
            @Override
            public void setupModule(SetupContext ctxt) {
                super.setupModule(ctxt);
                ctxt.addDeserializerModifier(new ValueDeserializerModifier() {
                    private static final long serialVersionUID = 1L;
                    @Override
                    public ValueDeserializer<?> modifyDeserializer(DeserializationConfig cfg,
                            BeanDescription.Supplier ref, ValueDeserializer<?> d) {
                        seen.put(ref.getBeanClass(), d);
                        return d;
                    }
                });
            }
        };
        return JsonMapper.builder()
                .addModule(capture)
                .addModule(new BlackbirdModule())
                .build();
    }

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
        assertEquals("BBReaderPlaceholder", seen.get(FactoryBean.class).getClass().getSimpleName(),
                "factory-creator bean did not engage a codec");
        assertEquals("a", actual.name);
        assertEquals(expected.viaFactory, actual.viaFactory,
                "construction path differs from stock (default creator is the factory)");
        assertTrue(actual.viaFactory);
    }
}
