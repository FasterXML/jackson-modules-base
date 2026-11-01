package tools.jackson.module.blackbird.deser;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonView;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.DeserializationProblemHandler;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Non-Jackson exception from handling of an unknown property gets the property
 * as its path. Unlike most of Blackbird's behavior this is not same as stock,
 * which does not wrap these.
 */
public class BBUnknownPropertyFailureTest extends BlackbirdTestBase
{
    public static class Pojo {
        public int a;
    }

    public record Rec(int a) { }

    @JsonDeserialize(builder = Built.Builder.class)
    public static class Built {
        public static class Builder {
            int a;
            public Builder withA(int v) { a = v; return this; }
            public Built build() { return new Built(); }
        }
    }

    public static class ViewA { }

    public static class ViewPojo {
        @JsonView(ViewA.class) public int a;
    }

    private static final List<Class<?>> TYPES = List.of(Pojo.class, Rec.class, Built.class);
    private static final List<String> DOCS = List.of("{'zzz':1}", "{'a':1,'zzz':1}", "{'zzz':1,'a':1}");

    private final DeserializationProblemHandler HANDLER = new DeserializationProblemHandler() {
        @Override
        public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser p,
                ValueDeserializer<?> deser, Object beanOrClass, String propName) {
            throw new IllegalStateException("handler failure");
        }
    };

    private final ObjectMapper MAPPER = mapperBuilder().addHandler(HANDLER).build();

    @Test
    public void wrappedWithDefaultSettings() throws Exception {
        for (Class<?> type : TYPES) {
            assertGeneratedReader(MAPPER, type);
            _verifyWrapped(MAPPER.readerFor(type));
        }
    }

    // Settings for stock's vanilla loop
    @Test
    public void wrappedWithVanillaSettings() throws Exception {
        final ObjectMapper mapper = mapperBuilder().addHandler(HANDLER)
                .enable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();
        for (Class<?> type : TYPES) {
            assertGeneratedReader(mapper, type);
            _verifyWrapped(mapper.readerFor(type));
        }
    }

    @Test
    public void wrappedUnderActiveView() throws Exception {
        assertGeneratedReader(MAPPER, ViewPojo.class);
        _verifyWrapped(MAPPER.readerFor(ViewPojo.class).withView(ViewA.class));
    }

    // Goes through stock code that the codec inherits
    @Test
    public void wrappedForUpdate() throws Exception {
        _verifyWrapped(MAPPER.readerForUpdating(new Pojo()));
    }

    @Test
    public void notWrappedIfDisabled() throws Exception {
        for (Class<?> type : TYPES) {
            assertThrows(IllegalStateException.class, () -> MAPPER.readerFor(type)
                    .without(DeserializationFeature.WRAP_EXCEPTIONS)
                    .readValue(a2q("{'zzz':1}")));
        }
    }

    // Has its path already: must not get the property twice
    @Test
    public void jacksonExceptionPassedAsIs() throws Exception {
        final ObjectMapper mapper = mapperBuilder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        for (Class<?> type : TYPES) {
            UnrecognizedPropertyException e = assertThrows(UnrecognizedPropertyException.class,
                    () -> mapper.readerFor(type).readValue(a2q("{'zzz':1}")));
            assertEquals(1, e.getPath().size(), e.getMessage());
            assertEquals("zzz", e.getPath().get(0).getPropertyName());
        }
    }

    private static void _verifyWrapped(ObjectReader r) {
        for (String doc : DOCS) {
            DatabindException e = assertThrows(DatabindException.class,
                    () -> r.readValue(a2q(doc)), doc);
            assertInstanceOf(IllegalStateException.class, e.getCause(), doc);
            assertEquals(1, e.getPath().size(), doc);
            assertEquals("zzz", e.getPath().get(0).getPropertyName(), doc);
        }
    }
}
