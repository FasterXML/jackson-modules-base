package tools.jackson.module.blackbird.ser;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonView;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.util.JsonGeneratorDelegate;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

// A property hidden by the active view is written as an omitted property
// on generators that cannot omit properties (CSV, for one): stock
// serializers call writeOmittedProperty for it, so generated writers must
// too.
public class BBCodecOmittedPropertyTest extends BlackbirdTestBase
{
    static class Public { }
    static class Internal { }

    @JsonPropertyOrder({ "id", "secret", "name", "note" })
    public static class ViewBean {
        public int id = 1;
        @JsonView(Internal.class)
        public String secret = "s";
        @JsonView(Public.class)
        public String name = "n";
        @JsonView(Internal.class)
        public String note = "x";
    }

    // Records omitted properties, as a generator that cannot omit them
    static class NoOmitGenerator extends JsonGeneratorDelegate {
        final List<String> omitted = new ArrayList<>();

        NoOmitGenerator(JsonGenerator d) {
            super(d);
        }

        @Override
        public boolean canOmitProperties() {
            return false;
        }

        @Override
        public JsonGenerator writeOmittedProperty(String propName) {
            omitted.add(propName);
            return this;
        }
    }

    private static List<String> omittedWith(ObjectMapper mapper, Class<?> view) throws Exception {
        try (NoOmitGenerator g = new NoOmitGenerator(mapper.createGenerator(new StringWriter()))) {
            mapper.writerWithView(view).writeValue(g, new ViewBean());
            return g.omitted;
        }
    }

    @Test
    public void testHiddenPropertiesWrittenAsOmitted() throws Exception {
        ObjectMapper mapper = newObjectMapper();
        assertGeneratedWriter(mapper, ViewBean.class);

        List<String> expected = omittedWith(newVanillaJSONMapper(), Public.class);
        assertEquals(List.of("secret", "note"), expected);
        assertEquals(expected, omittedWith(mapper, Public.class));
        assertEquals(List.of("name"), omittedWith(mapper, Internal.class));
    }

    // Without DEFAULT_VIEW_INCLUSION, a property with no view is left out
    // altogether by stock (no omitted placeholder), unlike a view mismatch
    @Test
    public void testViewlessPropertiesNotWrittenAsOmitted() throws Exception {
        ObjectMapper mapper = mapperBuilder()
                .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                .build();
        assertGeneratedWriter(mapper, ViewBean.class);

        List<String> expected = omittedWith(newVanillaJSONMapper().rebuild()
                .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                .build(), Public.class);
        assertEquals(List.of("secret", "note"), expected);
        assertEquals(expected, omittedWith(mapper, Public.class));
    }
}
