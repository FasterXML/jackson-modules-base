package tools.jackson.module.blackbird.codegen;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Class-data entries are named after JSON property names, which may hold
 * characters the JVM forbids in member names; those must not keep a bean
 * from getting its generated codecs.
 */
public class PropertyNameSanitizingTest extends BlackbirdTestBase
{
    public static class OddNames {
        @JsonProperty("a<b>")
        public String angles = "x";
        @JsonProperty("<init>")
        public int init = 1;
        @JsonProperty("p.q;r[s/t")
        public String others = "y";
        // sanitizes to the same name as "p.q;r[s/t"
        @JsonProperty("p$q$r$s$t")
        public String collides = "z";
    }

    @Test
    public void testForbiddenCharactersInPropertyNames() throws Exception {
        ObjectMapper mapper = newObjectMapper();
        assertGeneratedReader(mapper, OddNames.class);
        assertGeneratedWriter(mapper, OddNames.class);

        String json = mapper.writeValueAsString(new OddNames());
        assertEquals(newVanillaJSONMapper().writeValueAsString(new OddNames()), json);

        OddNames result = mapper.readValue(a2q(
                "{'a<b>':'1','<init>':2,'p.q;r[s/t':'3','p$q$r$s$t':'4'}"),
                OddNames.class);
        assertEquals("1", result.angles);
        assertEquals(2, result.init);
        assertEquals("3", result.others);
        assertEquals("4", result.collides);
    }
}
