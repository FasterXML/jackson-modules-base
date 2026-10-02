package tools.jackson.module.blackbird.deser;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

// Field-backed properties: the generator stores through putfield / reads
// through getfield for public fields, and reaches a non-public field through a
// lookup-derived handle. Behavior must match stock databind on every path.
public class BBCodecFieldTest extends BlackbirdTestBase
{
    @JsonPropertyOrder({ "name", "count", "total", "active", "tags", "child" })
    public static class FieldBean {
        public String name;
        public int count;
        public long total;
        public boolean active;
        public List<String> tags;
        public FieldChild child;
    }

    public static class FieldChild {
        public String label;
    }

    public static class FinalFieldBean {
        public final String name;
        public int count;

        public FinalFieldBean() {
            this("", 0);
        }

        @JsonCreator
        public FinalFieldBean(
                @JsonProperty("name") String name,
                @JsonProperty("count") int count) {
            this.name = name;
            this.count = count;
        }
    }

    public static class MixedBean {
        public String field;
        private int viaSetter;

        public int getViaSetter() { return viaSetter; }
        public void setViaSetter(int v) { viaSetter = v; }
    }

    public static class PrivateFieldBean {
        @JsonProperty("name")
        private String name;
        @JsonProperty("count")
        private int count;

        public String name() { return name; }
        public int count() { return count; }
    }

    private final ObjectMapper MAPPER = newObjectMapper();
    private final ObjectMapper VANILLA = newVanillaJSONMapper();

    @Test
    public void testPublicFieldValues() throws Exception {
        String json = a2q("{'name':'a','count':3,'total':9000000000,'active':true,"
                + "'tags':['x','y'],'child':{'label':'c'}}");
        FieldBean bean = MAPPER.readValue(json, FieldBean.class);
        assertEquals("a", bean.name);
        assertEquals(3, bean.count);
        assertEquals(9000000000L, bean.total);
        assertTrue(bean.active);
        assertEquals(List.of("x", "y"), bean.tags);
        assertEquals("c", bean.child.label);
    }

    @Test
    public void testPublicFieldReversedOrder() throws Exception {
        String json = a2q("{'child':{'label':'c'},'tags':['x'],'active':true,"
                + "'total':7,'count':2,'name':'a'}");
        FieldBean bean = MAPPER.readValue(json, FieldBean.class);
        FieldBean vanilla = VANILLA.readValue(json, FieldBean.class);
        assertEquals(vanilla.name, bean.name);
        assertEquals(vanilla.count, bean.count);
        assertEquals(vanilla.child.label, bean.child.label);
    }

    @Test
    public void testPublicFieldNullsAndUnknowns() throws Exception {
        String json = a2q("{'name':null,'extra':{'deep':1},'count':5,"
                + "'tags':null,'child':null}");
        FieldBean bean = MAPPER.readValue(json, FieldBean.class);
        assertNull(bean.name);
        assertEquals(5, bean.count);
        assertNull(bean.tags);
        assertNull(bean.child);
    }

    @Test
    public void testNullToPrimitiveFieldThrows() {
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue(a2q("{'count':null}"), FieldBean.class));
    }

    @Test
    public void testPublicFieldRoundTripMatchesVanilla() throws Exception {
        FieldBean bean = MAPPER.readValue(a2q("{'name':'a','count':3,'total':7,"
                + "'active':true,'tags':['x','y'],'child':{'label':'c'}}"), FieldBean.class);
        assertEquals(VANILLA.writeValueAsString(bean), MAPPER.writeValueAsString(bean));
    }

    @Test
    public void testFinalFieldMatchesVanilla() throws Exception {
        String json = a2q("{'name':'a','count':3}");
        FinalFieldBean bean = MAPPER.readValue(json, FinalFieldBean.class);
        assertEquals("a", bean.name);
        assertEquals(3, bean.count);
        assertEquals(VANILLA.writeValueAsString(bean), MAPPER.writeValueAsString(bean));
    }

    @Test
    public void testMixedFieldAndSetter() throws Exception {
        MixedBean bean = MAPPER.readValue(a2q("{'field':'a','viaSetter':7}"), MixedBean.class);
        assertEquals("a", bean.field);
        assertEquals(7, bean.getViaSetter());
        assertEquals(VANILLA.writeValueAsString(bean), MAPPER.writeValueAsString(bean));
    }

    @Test
    public void testPrivateFieldWithLookupAccelerates() throws Exception {
        Supplier<MethodHandles.Lookup> here = MethodHandles::lookup;
        ObjectMapper m = JsonMapper.builder()
                .addModule(new BlackbirdModule() {
                    @Override
                    protected Supplier<MethodHandles.Lookup> findLookupSupplier() {
                        return here;
                    }
                })
                .build();
        PrivateFieldBean bean = m.readValue(a2q("{'name':'a','count':3}"), PrivateFieldBean.class);
        assertEquals("a", bean.name());
        assertEquals(3, bean.count());
    }

    @Test
    public void testPrivateFieldWithoutLookupStillWorks() throws Exception {
        // The default lookup cannot reach the private field, so the property
        // demotes to the stock path; the value must still round-trip.
        PrivateFieldBean bean = MAPPER.readValue(a2q("{'name':'a','count':3}"),
                PrivateFieldBean.class);
        assertEquals("a", bean.name());
        assertEquals(3, bean.count());
    }

    @Test
    public void testFieldBeanEngagesCodec() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ValueDeserializerModifier capture = new ValueDeserializerModifier() {
            private static final long serialVersionUID = 1L;
            @Override
            public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config,
                    BeanDescription.Supplier ref, ValueDeserializer<?> deser) {
                seen.put(ref.getBeanClass(), deser);
                return deser;
            }
        };
        ObjectMapper m = JsonMapper.builder()
                .addModule(new SimpleModule("capture").setDeserializerModifier(capture))
                .addModule(new BlackbirdModule())
                .build();
        m.readValue(a2q("{'name':'a'}"), FieldBean.class);
        assertEquals("BBReaderPlaceholder", seen.get(FieldBean.class).getClass().getSimpleName());
    }
}
