package tools.jackson.module.blackbird.ser;

import java.util.Iterator;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ser.PropertyWriter;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

// A custom inclusion filter on a primitive getter suppresses what its `equals`
// matches, as with `BeanPropertyWriter`
public class PrimitiveInclusionFilterTest extends BlackbirdTestBase
{
    // Suppresses negative values (and only through `equals`)
    static class NegativeFilter {
        @Override
        public boolean equals(Object o) {
            return ((o instanceof Integer) && ((Integer) o < 0))
                    || ((o instanceof Long) && ((Long) o < 0L));
        }

        @Override
        public int hashCode() { return 0; }
    }

    // Suppresses `true` (and only through `equals`)
    static class TrueFilter {
        @Override
        public boolean equals(Object o) {
            return Boolean.TRUE.equals(o);
        }

        @Override
        public int hashCode() { return 0; }
    }

    @JsonPropertyOrder({ "i", "l", "b" })
    static class Custom {
        int i;
        long l;
        boolean b;

        Custom(int i, long l, boolean b) {
            this.i = i;
            this.l = l;
            this.b = b;
        }

        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = NegativeFilter.class)
        public int getI() { return i; }

        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = NegativeFilter.class)
        public long getL() { return l; }

        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = TrueFilter.class)
        public boolean getB() { return b; }
    }

    // As an array, which writes each property as an element (and a placeholder for
    // a suppressed one)
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    static class ArrayCustom extends Custom {
        ArrayCustom(int i, long l, boolean b) {
            super(i, l, b);
        }
    }

    // And the inclusions decided without a filter
    @JsonPropertyOrder({ "i", "l", "b" })
    static class Other {
        int i;
        long l;
        boolean b;

        Other(int i, long l, boolean b) {
            this.i = i;
            this.l = l;
            this.b = b;
        }

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        public int getI() { return i; }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public long getL() { return l; }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public boolean getB() { return b; }
    }

    private final ObjectMapper MAPPER = newObjectMapper();

    @Test
    public void testUsesGeneratedGetters() throws Exception
    {
        for (Class<?> type : new Class<?>[] { Custom.class, ArrayCustom.class, Other.class }) {
            assertEquals(IntPropertyWriter.class, _findWriter(MAPPER, type, "i").getClass());
            assertEquals(LongPropertyWriter.class, _findWriter(MAPPER, type, "l").getClass());
            assertEquals(BooleanPropertyWriter.class, _findWriter(MAPPER, type, "b").getClass());
        }
    }

    @Test
    public void testCustomFilter() throws Exception
    {
        assertEquals(a2q("{'i':1,'l':2,'b':false}"), MAPPER.writeValueAsString(new Custom(1, 2L, false)));
        assertEquals("{}", MAPPER.writeValueAsString(new Custom(-1, -2L, true)));
        assertEquals("[1,2,false]", MAPPER.writeValueAsString(new ArrayCustom(1, 2L, false)));
        assertEquals("[null,null,null]", MAPPER.writeValueAsString(new ArrayCustom(-1, -2L, true)));
    }

    @Test
    public void testOutputMatchesVanilla() throws Exception
    {
        ObjectMapper vanilla = newVanillaJSONMapper();
        for (int i : new int[] { -1, 0, 1 }) {
            for (boolean b : new boolean[] { false, true }) {
                Object[] beans = { new Custom(i, i, b), new ArrayCustom(i, i, b), new Other(i, i, b) };
                for (Object bean : beans) {
                    String desc = bean.getClass().getSimpleName() + ", " + i + ", " + b;
                    assertEquals(vanilla.writeValueAsString(bean), MAPPER.writeValueAsString(bean), desc);
                    // and through a buffer
                    assertEquals(String.valueOf(vanilla.valueToTree(bean)),
                            String.valueOf(MAPPER.valueToTree(bean)), desc);
                }
            }
        }
    }

    private static PropertyWriter _findWriter(ObjectMapper mapper, Class<?> type, String name) throws Exception
    {
        BeanSerializerBase ser = (BeanSerializerBase) mapper._serializationContext()
                .findValueSerializer(type);
        for (Iterator<PropertyWriter> it = ser.properties(); it.hasNext(); ) {
            PropertyWriter writer = it.next();
            if (writer.getName().equals(name)) {
                return writer;
            }
        }
        throw new AssertionError("No property '" + name + "' for " + type.getName());
    }
}
