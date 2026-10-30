package tools.jackson.module.blackbird.deser;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.TokenBuffer;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A token that is neither a name nor END_OBJECT where a name is expected
 * (possible from a non-JSON parser or a replayed buffer). Stock's vanilla
 * loop reports it as unexpected, unless an unknown name came before it: then
 * _vanillaDeserializeWithUnknown returns the bean as it is. Stock takes the
 * vanilla loop only with DEFAULT_VIEW_INCLUSION on (off by default in 3.x);
 * otherwise its general loop reports the token either way. Every case
 * compares against a vanilla mapper.
 */
public class BBReaderOddTokenTest extends BlackbirdTestBase
{
    public static class Plain {
        public String s;
        public int i;
    }

    private final ObjectMapper mapper = newObjectMapper();
    private final ObjectMapper vanilla = newVanillaJSONMapper();

    private final ObjectMapper viewInclusionMapper = mapperBuilder()
            .enable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();
    private final ObjectMapper viewInclusionVanilla = JsonMapper.builder()
            .enable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();

    @Test
    public void oddTokenAfterUnknownReturnsBean() throws Exception
    {
        assertGeneratedReader(viewInclusionMapper, Plain.class);
        Plain expected = read(viewInclusionVanilla, true);
        Plain actual = read(viewInclusionMapper, true);
        assertEquals("a", expected.s);
        assertEquals("a", actual.s);
        assertEquals(expected.i, actual.i);
    }

    @Test
    public void oddTokenAfterUnknownFailsWithoutVanillaLoop() throws Exception
    {
        assertGeneratedReader(mapper, Plain.class);
        assertSameFailure(vanilla, mapper, true);
    }

    @Test
    public void oddTokenWithoutUnknownFails() throws Exception
    {
        assertGeneratedReader(mapper, Plain.class);
        assertSameFailure(vanilla, mapper, false);
        assertGeneratedReader(viewInclusionMapper, Plain.class);
        assertSameFailure(viewInclusionVanilla, viewInclusionMapper, false);
    }

    private static void assertSameFailure(ObjectMapper stock, ObjectMapper bb,
            boolean withUnknown)
    {
        MismatchedInputException expected = assertThrows(MismatchedInputException.class,
                () -> read(stock, withUnknown));
        MismatchedInputException actual = assertThrows(MismatchedInputException.class,
                () -> read(bb, withUnknown));
        assertEquals(expected.getOriginalMessage(), actual.getOriginalMessage());
    }

    // {"s":"a", ["x":1,] 3, "i":5}: a bare number where a name belongs.
    private static Plain read(ObjectMapper m, boolean withUnknown) throws Exception
    {
        TokenBuffer tb = TokenBuffer.forGeneration();
        tb.writeStartObject();
        tb.writeName("s");
        tb.writeString("a");
        if (withUnknown) {
            tb.writeName("x");
            tb.writeNumber(1);
        }
        tb.writeNumber(3);
        tb.writeName("i");
        tb.writeNumber(5);
        tb.writeEndObject();
        try (JsonParser p = tb.asParser(ObjectReadContext.empty())) {
            return m.readerFor(Plain.class)
                    // Stock's early return leaves the rest of the object
                    // unread.
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                            DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(p);
        }
    }
}
