package tools.jackson.module.blackbird.deser;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;

import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class BBCodecEngagementTest extends BlackbirdTestBase
{
    public static class PublicBean {
        private String name;
        private int count;
        private long total;
        private boolean active;
        private List<String> tags;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
    }

    public record PublicRec(String name, int count, long total, boolean active, List<String> tags) {}

    @tools.jackson.databind.annotation.JsonDeserialize(builder = BuilderBean.Builder.class)
    public static class BuilderBean {
        private final String name;
        private final int count;
        private final List<String> tags;

        private BuilderBean(String name, int count, List<String> tags) {
            this.name = name;
            this.count = count;
            this.tags = tags;
        }

        public String getName() { return name; }
        public int getCount() { return count; }
        public List<String> getTags() { return tags; }

        @tools.jackson.databind.annotation.JsonPOJOBuilder(withPrefix = "")
        public static class Builder {
            private String name;
            private int count;
            private List<String> tags;

            public Builder name(String name) { this.name = name; return this; }
            public Builder count(int count) { this.count = count; return this; }
            public Builder tags(List<String> tags) { this.tags = tags; return this; }
            public BuilderBean build() { return new BuilderBean(name, count, tags); }
        }
    }

    private final ObjectMapper MAPPER = newObjectMapper();

    @Test
    public void testGeneratedRecordCodecValues() throws Exception {
        PublicRec rec = MAPPER.readValue(
                "{\"name\":\"a\",\"count\":3,\"total\":9000000000,\"active\":true,\"tags\":[\"x\"]}",
                PublicRec.class);
        assertEquals(new PublicRec("a", 3, 9000000000L, true, List.of("x")), rec);

        PublicRec reversed = MAPPER.readValue(
                "{\"tags\":[\"x\"],\"active\":true,\"total\":9000000000,\"count\":3,\"name\":\"a\"}",
                PublicRec.class);
        assertEquals(rec, reversed);
    }

    @Test
    public void testGeneratedRecordCodecNullsUnknownsMissing() throws Exception {
        ObjectMapper vanilla = newVanillaJSONMapper();
        String doc = "{\"name\":null,\"junk\":{\"a\":[1]},\"count\":3}";
        assertEquals(vanilla.readValue(doc, PublicRec.class),
                MAPPER.readValue(doc, PublicRec.class));
        String primNull = "{\"count\":null,\"name\":\"n\"}";
        assertThrows(MismatchedInputException.class,
                () -> vanilla.readValue(primNull, PublicRec.class));
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue(primNull, PublicRec.class));
    }

    @Test
    public void testGeneratedBuilderCodecValues() throws Exception {
        String doc = "{\"name\":\"b\",\"junk\":[{}],\"count\":7,\"tags\":[\"t\"]}";
        BuilderBean bean = MAPPER.readValue(doc, BuilderBean.class);
        assertEquals("b", bean.getName());
        assertEquals(7, bean.getCount());
        assertEquals(List.of("t"), bean.getTags());
        BuilderBean vanillaBean = newVanillaJSONMapper().readValue(doc, BuilderBean.class);
        assertEquals(vanillaBean.getName(), bean.getName());
        assertEquals(vanillaBean.getCount(), bean.getCount());
        assertEquals(vanillaBean.getTags(), bean.getTags());
    }

    @Test
    public void testEngineEngagesForBuilderBean() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> MAPPER.readValue("[1]", BuilderBean.class));
        assertThrows(MismatchedInputException.class,
                () -> newVanillaJSONMapper().readValue("[1]", BuilderBean.class));
    }

    @Test
    public void testEngineEngagesForPublicRecord() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> MAPPER.readValue("[1]", PublicRec.class));
        assertThrows(MismatchedInputException.class,
                () -> newVanillaJSONMapper().readValue("[1]", PublicRec.class));
    }

    @Test
    public void testGeneratedCodecValues() throws Exception {
        PublicBean bean = MAPPER.readValue(
                "{\"name\":\"a\",\"count\":3,\"total\":9000000000,\"active\":true,\"tags\":[\"x\",\"y\"]}",
                PublicBean.class);
        assertEquals("a", bean.getName());
        assertEquals(3, bean.getCount());
        assertEquals(9000000000L, bean.getTotal());
        assertTrue(bean.isActive());
        assertEquals(List.of("x", "y"), bean.getTags());
    }

    @Test
    public void testGeneratedCodecNullsAndUnknowns() throws Exception {
        PublicBean bean = MAPPER.readValue(
                "{\"name\":null,\"junk\":{\"deep\":[1,2]},\"active\":false}",
                PublicBean.class);
        assertNull(bean.getName());
        assertEquals(0, bean.getCount());
        assertFalse(bean.isActive());
        assertNull(bean.getTags());
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue("{\"count\":null}", PublicBean.class));
        assertThrows(MismatchedInputException.class,
                () -> newVanillaJSONMapper().readValue("{\"count\":null}", PublicBean.class));
    }

    // The generated codec rejects a non-object where a property name is
    // expected with IllegalStateException, where the stock deserializer
    // reports MismatchedInputException: the distinct failure type proves the
    // codec, not the stock path, handled the read.
    @Test
    public void testEngineEngagesForPublicBean() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> MAPPER.readValue("[1]", PublicBean.class));
        ObjectMapper vanilla = newVanillaJSONMapper();
        assertThrows(MismatchedInputException.class,
                () -> vanilla.readValue("[1]", PublicBean.class));
    }
}
